package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.util.HttpUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import cz.iocb.idsm.debugger.model.Tree.Node;
import org.springframework.web.util.UriComponentsBuilder;

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

    private Map<Long, Tree<EndpointCall>> queryExecutionMap = new HashMap<>();
    private AtomicLong queryCounter = new AtomicLong(0);
    private AtomicLong endpointCallCounter = new AtomicLong(0);

    @Override
    public Node<EndpointCall> createServiceEndpointNode(Node<SparqlQueryInfo> queryNode, Node<EndpointCall> parentNode) {
        logger.debug("createServiceEndpointNode - start");

        EndpointCall endpointCall = new EndpointCall(parentNode.getData().getQueryId(), endpointCallCounter.getAndAdd(1), queryNode);

        logger.debug("createServiceEndpointNode - end");
        return parentNode.addNode(endpointCall);
    }

    @Override
    public Node<EndpointCall> createQueryEndpointRoot(URI endpoint) {
        Long queryId = queryCounter.addAndGet(1);

        logger.debug(format("createQueryEndpointRoot - start. queryId=%s , endpoint=%s", queryId, endpoint));

        Tree<SparqlQueryInfo> queryTree = sparqlQueryService.createQueryTree(endpoint.toString(), sparqlRequest.getQuery(), queryId);

        EndpointCall endpointRoot = new EndpointCall(queryId, endpointCallCounter.getAndAdd(1), queryTree.getRoot());
        Tree<EndpointCall> endpointTree = new Tree<>(endpointRoot);
        queryExecutionMap.put(queryId, endpointTree);

        logger.debug(format("createQueryEndpointRoot - end. queryId=%s", queryId));

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

    public HttpResponse<String> callEndpoint(URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode) {

        logger.debug(format("callEndpoint - start. queryId=%s , endpoint=%s", queryId, endpoint));

        EndpointCall endpointCall = endpointCallNode.getData();
        endpointCall.setStartTime(System.currentTimeMillis());
        endpointCall.setState(EndpointNodeState.IN_PROGRESS);

        if(endpointCallNode.getParent() == null) {
            endpointCall.setSeqId(1L);
        } else {
            endpointCall.setSeqId(endpointCallNode.getParent().getChildren().stream()
                    .filter(en -> en.getData().getQueryNode().getData().nodeId == endpointCall.getQueryNode().getData().nodeId && endpointCall.getState() != EndpointNodeState.NONE)
                    .count() + 1);
        }

        String proxyQuery = sparqlQueryService.injectOuterServices(sparqlRequest.getQuery(), endpointCallNode.getData());

        HttpRequest request = createHttpRequest(endpoint, proxyQuery);
        saveRequest(request, queryId, endpointCall.getNodeId());

        HttpResponse<String> response = null;
        try {

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            saveResponse(body, queryId, endpointCall.getNodeId());

            endpointCall.setState(EndpointNodeState.SUCCESS);
            endpointCall.setHttpState(response.statusCode());

            logger.debug(format("callEndpoint - end. queryId=%d, nodeId=%d", queryId, endpointCall.getNodeId()));
        } catch (IOException e) {
            logger.error(format("I/O Error during request. queryId=%d, nodeId=%d", endpointCall.getNodeId()));
            throw new SparqlDebugException("I/O Error during request.", e);
        } catch (InterruptedException e) {
            logger.error("Request interrupted.");
            Thread.currentThread().interrupt();
        }

        return response;
    }

    @Override
    public Optional<Node<EndpointCall>> getEndpointNode(Long queryId, Long nodeId) {

        logger.debug(format("getEndpointNode - start. queryId=%s , nodeId=%s", queryId, nodeId));

        Tree<EndpointCall> endpointCallTree = queryExecutionMap.get(queryId);

        Optional<Node<EndpointCall>> result;
        if(endpointCallTree == null) {
            result =  Optional.empty();
        } else {
            result =  endpointCallTree.getRoot().findNode(endpointCall -> endpointCall.getNodeId() == nodeId);
        }

        logger.debug(format("getEndpointNode - start. queryId=%s , nodeId=%s, result=%s", queryId, nodeId, result));

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

        sparqlRequest.getHeaderMap().entrySet().stream()
                .filter(entry -> !(entry.getKey().equalsIgnoreCase(HttpHeaders.HOST) ||
                        entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) ||
                        entry.getKey().equalsIgnoreCase(HttpHeaders.CONNECTION)))
                .forEach(entry -> requestBuilder.header(entry.getKey(), entry.getValue()));

        HttpRequest request = requestBuilder.build();

        return request;
    }

    private void saveRequest(HttpRequest request, Long queryId, Long nodeId) {
        String fileName = composeFileName("request", queryId.toString(), nodeId.toString());

        try {

            logger.debug(format("saveRequest - start. queryId=%s , nodeId=%s, request=%s", queryId, nodeId,
                    HttpUtil.prettyPrintRequest(request)));

            File tempFile = File.createTempFile(fileName, ".tmp");
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

    private void saveResponse(String responseBody, Long queryId, Long nodeId) {
        String fileName = composeFileName("response", queryId.toString(), nodeId.toString());

        try {
            File tempFile = File.createTempFile(fileName, ".tmp");
            tempFile.deleteOnExit();

            FileWriter fileWriter = new FileWriter(tempFile);
            fileWriter.write(HttpUtil.prettyPrintResponse(responseBody));
            fileWriter.close();
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to write request to file.", e);
        }

    }

    private String composeFileName(String... parts) {
        return Arrays.stream(parts).collect(Collectors.joining("_"))+"_";
    }
}
