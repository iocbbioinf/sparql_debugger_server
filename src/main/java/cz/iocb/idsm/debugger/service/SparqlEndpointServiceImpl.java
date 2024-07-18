package cz.iocb.idsm.debugger.service;

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
import org.springframework.web.util.UriComponentsBuilder;

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

    private Map<Long, Tree<EndpointCall>> queryExecutionMap = new ConcurrentHashMap<>();
    private AtomicLong queryCounter = new AtomicLong(0);
    private AtomicLong endpointCallCounter = new AtomicLong(0);

    private Set<Long> cancellingQuerySet = Collections.synchronizedSet(new HashSet<>());


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

        logger.debug("createQueryEndpointRoot - end. queryId={}", queryId);

        return endpointTree.getRoot();
    }

    @Override
    public Optional<Tree<EndpointCall>> getQueryTree(Long queryId) {
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

        saveResponse(response.body(), queryId, endpointCall.getNodeId());

        if(response.statusCode() >= 200 && response.statusCode() < 300) {
            endpointCall.setState(EndpointNodeState.SUCCESS);
        } else {
            endpointCall.setState(EndpointNodeState.ERROR);
        }

        endpointCall.setHttpStatus(response.statusCode());
        endpointCall.setDuration(System.currentTimeMillis() - endpointCall.getStartTime());

        endpointCall.setContentType(response.headers().allValues(HttpHeaderNames.CONTENT_TYPE.toString()));
        endpointCall.setContentEncoding(response.headers().allValues(HttpHeaderNames.CONTENT_ENCODING.toString()));

        endpointCallNode.updateNode();
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
        FileSystemResource result = new FileSystemResource(fileId.getPath());

        return result;
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
        cancellingQuerySet.add(queryId);
        Tree<EndpointCall> callTree = queryExecutionMap.get(queryId);
        cancelCallTreeThreads(callTree.getRoot());
    }

    @Override
    public void deleteQuery(Long queryId) {
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


}
