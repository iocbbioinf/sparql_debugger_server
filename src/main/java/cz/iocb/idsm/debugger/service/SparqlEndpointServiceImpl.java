package cz.iocb.idsm.debugger.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.util.HttpUtil;
import io.netty.handler.codec.http.HttpHeaderNames;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import cz.iocb.idsm.debugger.model.Tree.Node;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.util.UriComponentsBuilder;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static cz.iocb.idsm.debugger.model.FileId.FILE_TYPE.REQUEST;
import static cz.iocb.idsm.debugger.model.FileId.FILE_TYPE.RESPONSE;
import static java.lang.String.format;


@Service
public class SparqlEndpointServiceImpl implements SparqlEndpointService{

    @Autowired
    private SparqlQueryService sparqlQueryService;

    @Autowired
    private HttpClient httpClient;

    @Resource(name = "sparqlRequestBean")
    SparqlRequest sparqlRequest;

    private static final Logger logger = LoggerFactory.getLogger(SparqlEndpointServiceImpl.class);

    private final Map<Long, Tree<EndpointCall>> queryExecutionMap = new ConcurrentHashMap<>();
    private final AtomicLong queryCounter = new AtomicLong(0);
    private final AtomicLong endpointCallCounter = new AtomicLong(0);

    private final Set<Long> cancellingQuerySet = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    private SessionScopeList sessionQueryList;

    @Override
    public Node<EndpointCall> createServiceEndpointNode(String endpoint, Node<SparqlQueryInfo> queryNode, Node<EndpointCall> parentNode, Long serviceCallId) {
        logger.debug("createServiceEndpointNode - start");

        EndpointCall endpointCall = new EndpointCall(parentNode.getData().getQueryId(), endpointCallCounter.getAndAdd(1),
                queryNode, parentNode.getData().getNodeId(), endpoint, serviceCallId);

        logger.debug("createServiceEndpointNode - end");
        return parentNode.addNode(endpointCall);
    }

    @Override
    public Node<EndpointCall> createQueryEndpointRoot(URI endpoint) {
        Long queryId = queryCounter.addAndGet(1);

        logger.debug("createQueryEndpointRoot - start. queryId={} , endpoint={}", queryId, endpoint);

        Tree<SparqlQueryInfo> queryTree = sparqlQueryService.createQueryTree(endpoint.toString(), sparqlRequest.getQuery(), queryId);

        EndpointCall endpointRoot = new EndpointCall(queryId, endpointCallCounter.getAndAdd(1), queryTree.getRoot(), null, endpoint.toString(), 1L);
        Tree<EndpointCall> endpointTree = new Tree<>(endpointRoot,
                node -> {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        try {
                            node.getTree().getEmitter().send(node.getData());
                        } catch (IOException e) {
                            node.getTree().getEmitter().completeWithError(e);
                        }
                    });
                    executor.shutdown();
                });

        queryExecutionMap.put(queryId, endpointTree);

        sessionQueryList.add(queryId);

        logger.debug("createQueryEndpointRoot - end. queryId={}", queryId);

        return endpointTree.getRoot();
    }

    @Override
    public Optional<Tree<EndpointCall>> getQueryTree(Long queryId) {
        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        Tree<EndpointCall> queryTree = queryExecutionMap.get(queryId);

        if(queryTree == null) {
            return Optional.empty();
        } else {
            return Optional.of(queryTree);
        }
    }

    public HttpRequest prepareEndpointToCall(URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode) {

        logger.debug("callEndpoint - start. queryId={} , endpoint={}", queryId, endpoint);

        EndpointCall endpointCall = endpointCallNode.getData();
        endpointCall.setStartTime(System.currentTimeMillis());
        endpointCall.setState(EndpointNodeState.IN_PROGRESS);

        if(endpointCallNode.getParent() == null) {
            endpointCall.setSeqId(1L);
        } else {
            endpointCall.setSeqId(endpointCallNode.getParent().getChildren().stream()
                    .filter(en -> en.getData().getServiceCallId().equals(endpointCall.getServiceCallId()) && endpointCall.getState() != EndpointNodeState.NONE)
                    .count());
        }

        String proxyQuery = sparqlQueryService.injectOuterServices(sparqlRequest.getQuery(), endpointCallNode.getData());

        HttpRequest request = createHttpRequest(endpoint, proxyQuery);
        saveRequest(request, queryId, endpointCall.getNodeId());

        return request;
    }

    @Override
    public HttpResponse<byte[]> callEndpointSync(HttpRequest request, URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode) {

        HttpResponse<byte[]> response = null;
        EndpointCall endpointCall = endpointCallNode.getData();

        try {
            if(cancellingQuerySet.contains(queryId)) {
                return null;
            }

            endpointCall.getCallThread().set(Thread.currentThread());

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            processResponse(response, endpointCall, endpointCallNode, queryId);
            endpointCall.getCallThread().set(null);

            logger.debug("callEndpoint - end. queryId={}, nodeId={}", queryId, endpointCall.getNodeId());
        } catch (IOException e) {
            logger.error("I/O Error during request. queryId={}, nodeId={}", queryId, endpointCall.getNodeId());

            endpointCall.setState(EndpointNodeState.ERROR);
            endpointCallNode.updateNode();

            throw new SparqlDebugException("I/O Error during request.", e);
        } catch (InterruptedException e) {
            logger.error("Request interrupted.");

            endpointCall.setState(EndpointNodeState.ERROR);
            endpointCallNode.updateNode();

            Thread.currentThread().interrupt();
        }

        return response;
    }

    private void processResponse(HttpResponse<byte[]> response, EndpointCall endpointCall, Node<EndpointCall> endpointCallNode, Long queryId) {

        if(response.statusCode() >= 200 && response.statusCode() < 300) {
            endpointCall.setState(EndpointNodeState.SUCCESS);
        } else {
            endpointCall.setState(EndpointNodeState.ERROR);
        }

        endpointCall.setHttpStatus(response.statusCode());
        endpointCall.setEndTime(System.currentTimeMillis());

        endpointCall.setContentType(response.headers().allValues(HttpHeaderNames.CONTENT_TYPE.toString()));
        endpointCall.setContentEncoding(response.headers().allValues(HttpHeaderNames.CONTENT_ENCODING.toString()));

        SparqlResultType resultType = getResultType(endpointCall.getContentType());
        if(resultType != null) {
            endpointCall.setResultType(resultType.contentType);
        }

        Long resultsCount = getResultCount(new ByteArrayInputStream(response.body()), resultType);
        endpointCall.setResultsCount(resultsCount);

        saveResponse(response.body(), queryId, endpointCall.getNodeId());

        endpointCallNode.updateNode();
    }

    private SparqlResultType getResultType(List<String> contentType) {
        String contentTypes = contentType.stream().collect(Collectors.joining(";"));

        if(contentTypes.toLowerCase().contains(SparqlResultType.XML.contentType)){
            return SparqlResultType.XML;
        }
        if(contentTypes.toLowerCase().contains(SparqlResultType.JSON.contentType)){
            return SparqlResultType.JSON;
        }
        if(contentTypes.toLowerCase().contains(SparqlResultType.CSV.contentType)){
            return SparqlResultType.CSV;
        }
        if(contentTypes.toLowerCase().contains(SparqlResultType.HTML.contentType)){
            return SparqlResultType.HTML;
        }


        return null;
    }

    @Override
    public void callEndpointAsync(HttpRequest request, URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode) {
        EndpointCall endpointCall = endpointCallNode.getData();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
//                    endpointCall.getCallThread().set(Thread.currentThread());
                    processResponse(resp, endpointCall, endpointCallNode, queryId);
                    endpointCall.getCallThread().set(null);
                })
                .exceptionally(e -> {
                    logger.error("Error during request. queryId={}, nodeId={} error={}",  queryId, endpointCall.getNodeId(), e.getMessage());

                    endpointCall.setState(EndpointNodeState.ERROR);
                    endpointCallNode.updateNode();
                    return null;
                });
    }

    @Override
    public Optional<Node<EndpointCall>> getEndpointNode(Long queryId, Long nodeId) {

        logger.debug("getEndpointNode - start. queryId={} , nodeId={}", queryId, nodeId);

        Tree<EndpointCall> endpointCallTree = queryExecutionMap.get(queryId);

        Optional<Node<EndpointCall>> result;
        if(endpointCallTree == null) {
            result =  Optional.empty();
        } else {
            result =  endpointCallTree.getRoot().findNode(endpointCall -> endpointCall.getNodeId().equals(nodeId));
        }

        logger.debug("getEndpointNode - start. queryId={} , nodeId={}, result={}", queryId, nodeId, result);

        return result;
    }

    @Override
    public FileSystemResource getFile(FileId fileId) {
        if(!queryIsInSession(fileId.getQueryId())) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        FileSystemResource result = new FileSystemResource(fileId.getPath());

        return result;
    }

    private Boolean queryIsInSession(Long queryId) {
        return sessionQueryList.contains(queryId);
    }

    private HttpRequest createHttpRequest(URI endpoint, String query) {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        switch (sparqlRequest.getType()) {
            case GET -> {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(endpoint.toString())
                        .queryParam(HttpUtil.PARAM_QUERY, query);
                if (sparqlRequest.getDefaultGraphUri() != null) {
                    uriBuilder.queryParam(HttpUtil.PARAM_DEFAULT_GRAPH_URI, sparqlRequest.getDefaultGraphUri());
                }
                if (sparqlRequest.getNamedGraphUri() != null) {
                    uriBuilder.queryParam(HttpUtil.PARAM_NAMED_GRAPH_URI, sparqlRequest.getNamedGraphUri());
                }

                requestBuilder
                        .uri(uriBuilder.build().toUri())
                        .GET();
            }
            case POST_FORM -> {
                Map<String, String> paramMap = new HashMap<>();
                paramMap.put(HttpUtil.PARAM_QUERY, query);
                if (sparqlRequest.getDefaultGraphUri() != null) {
                    paramMap.put(HttpUtil.PARAM_DEFAULT_GRAPH_URI, sparqlRequest.getDefaultGraphUri());
                }
                if (sparqlRequest.getNamedGraphUri() != null) {
                    paramMap.put(HttpUtil.PARAM_NAMED_GRAPH_URI, sparqlRequest.getNamedGraphUri());
                }

                String formData = buildFormData(paramMap);

                requestBuilder
                        .uri(endpoint)
                        .POST(HttpRequest.BodyPublishers.ofString(formData));
            }
            case POST_PLAIN -> {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(endpoint.toString());
                if (sparqlRequest.getDefaultGraphUri() != null) {
                    uriBuilder.queryParam(HttpUtil.PARAM_DEFAULT_GRAPH_URI, sparqlRequest.getDefaultGraphUri());
                }
                if (sparqlRequest.getNamedGraphUri() != null) {
                    uriBuilder.queryParam(HttpUtil.PARAM_NAMED_GRAPH_URI, sparqlRequest.getNamedGraphUri());
                }

                requestBuilder
                        .uri(uriBuilder.build().toUri())
                        .POST(HttpRequest.BodyPublishers.ofString(query));
            }
        }

        if(sparqlRequest.getHeaderMap() != null) {
            sparqlRequest.getHeaderMap().entrySet().stream()
                    .filter(entry -> !(entry.getKey().equalsIgnoreCase(HttpHeaders.HOST) ||
                            entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) ||
                            entry.getKey().equalsIgnoreCase(HttpHeaders.CONNECTION)))
                    .forEach(entry -> requestBuilder.header(entry.getKey(), entry.getValue()));
        }

        HttpRequest request = requestBuilder.build();

        return request;
    }

    @Override
    public void cancelQuery(Long queryId) {
        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        cancellingQuerySet.add(queryId);
        Tree<EndpointCall> callTree = queryExecutionMap.get(queryId);
        cancelCallTreeThreads(callTree.getRoot());
    }

    @Override
    public void deleteQuery(Long queryId) {
        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        queryExecutionMap.remove(queryId);
        sparqlQueryService.deleteQuery(queryId);
        deleteReqRespFiles(queryId);
        cancellingQuerySet.remove(queryId);
    }

    private void cancelCallTreeThreads(Node<EndpointCall> node) {
        if(node == null) {
            return;
        }

        node.getChildren().forEach(this::cancelCallTreeThreads);

        if(node.getData().getCallThread().get() != null) {
            node.getData().getCallThread().get().interrupt();
        }
    }

    private void deleteReqRespFiles(Long queryId) {
        File tmpDir = new File(FileId.TMP_DIR);

        File[] files = tmpDir.listFiles((dir, name) -> FileId.isQueryReqResp(name, queryId));

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    private void saveRequest(HttpRequest request, Long queryId, Long nodeId) {
        FileId fileId = new FileId(REQUEST, queryId, nodeId);
        try {
            logger.debug("saveRequest - start. queryId={} , nodeId={}, request={}", queryId, nodeId,
                    HttpUtil.prettyPrintRequest(request));

            File tempFile = new File(fileId.getPath());
            tempFile.deleteOnExit();

            FileWriter fileWriter = new FileWriter(tempFile);
            fileWriter.write(HttpUtil.prettyPrintRequest(request));
            fileWriter.close();
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to write request to file.", e);
        }
    }

    private static String buildFormData(Map<String, String> data) {
        return data.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private void saveResponse(byte[] responseBody, Long queryId, Long nodeId) {
        FileId fileId = new FileId(RESPONSE, queryId, nodeId);

        try (FileOutputStream output = new FileOutputStream(fileId.getPath())) {;
            output.write(responseBody);
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to write request to file.", e);
        }
    }

    @Override
    public void deleteQueries(Long cutoff) {
        Set<Long> queriesToDelete = queryExecutionMap.entrySet().stream()
                .filter(entry ->
                    entry.getValue().getRoot().findNode(endpointCall ->
                        endpointCall.getStartTime() < cutoff
                    ).isPresent()
                ).map(entry -> entry.getKey()).collect(Collectors.toSet());

        queriesToDelete.forEach(queryId -> deleteQuery(queryId));

        logger.debug("Number of deleted queries = {} ", queriesToDelete.size());
    }

    @Override
    public Long getResultCount(InputStream resultStream, SparqlResultType resultType) {
        if(resultType == null) {
            return null;
        }

        try {
            if (resultType == SparqlResultType.JSON) {
                return countJsonResults(resultStream);
            } else if (resultType == SparqlResultType.XML) {
                return countXmlResults(resultStream);
            } else if (resultType == SparqlResultType.CSV) {
                return countCsvResults(resultStream);
            }
        } catch (Exception e) {
            throw new SparqlDebugException("unable to process results", e);
        }

        return null;
    }


    private Long countJsonResults(InputStream inputStream) throws Exception {
        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(inputStream);
        Long count = 0L;

        while (!parser.isClosed()) {
            JsonToken token = parser.nextToken();
            if (JsonToken.FIELD_NAME.equals(token) && "result".equals(parser.getCurrentName())) {
                count++;
            }
        }

        return count;
    }

    private Long countXmlResults(InputStream inputStream) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        ResultCountingHandler handler = new ResultCountingHandler();
        saxParser.parse(new InputSource(inputStream), handler);
        return handler.getCount();
    }

    private Long countCsvResults(InputStream inputStream) throws Exception {
        Long count = 0L;
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        while ((reader.readLine()) != null) {
            count++;
        }

        return count - 1;
    }


    private static class ResultCountingHandler extends DefaultHandler {
        private Long count = 0L;

        public Long getCount() {
            return count;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("result".equals(qName)) {
                count++;
            }
        }
    }
}
