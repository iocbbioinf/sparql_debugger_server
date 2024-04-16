package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.util.HttpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import cz.iocb.idsm.debugger.model.Tree.Node;


@Service
public class SparqlEndpointServiceImpl implements SparqlEndpointService{

    @Autowired
    private SparqlQueryService sparqlQueryService;

    @Autowired
    private HttpClient httpClient;

    private Map<Long, Tree<EndpointCall>> queryExecutionMap = new HashMap<>();
    private AtomicLong queryCounter = new AtomicLong(0);
    private AtomicLong endpointCallCounter = new AtomicLong(0);

    @Override
    public Node<EndpointCall> createServiceEndpointNode(Node<SparqlQueryInfo> queryNode, Node<EndpointCall> parentNode) {
        EndpointCall endpointCall = new EndpointCall(parentNode.getData().queryId, endpointCallCounter.getAndAdd(1), queryNode);
        return parentNode.addNode(endpointCall);
    }

    @Override
    public Node<EndpointCall> createQueryEndpointRoot(String endpoint, String query) {
        Long queryId = queryCounter.addAndGet(1);
        Tree<SparqlQueryInfo> queryTree = sparqlQueryService.createQueryTree(endpoint, query, queryId);

        EndpointCall endpointRoot = new EndpointCall(queryId, endpointCallCounter.getAndAdd(1), queryTree.getRoot());
        Tree<EndpointCall> endpointTree = new Tree<>(endpointRoot);
        queryExecutionMap.put(queryId, endpointTree);

        return endpointTree.getRoot();
    }

    public void callEndpoint(Long queryId, Map<String, String> headerMap, String query, Node<EndpointCall> endpointCallNode) {

        EndpointCall endpointCall = endpointCallNode.getData();
        endpointCall.startTime = System.currentTimeMillis();
        endpointCall.state = EndpointNodeState.IN_PROGRESS;

        if(endpointCallNode.getParent() == null) {
            endpointCall.seqId = 1L;
        } else {
            endpointCall.seqId = endpointCallNode.getParent().getChildren().stream()
                    .filter(en -> en.getData().queryNode.getData().nodeId == endpointCall.queryNode.getData().nodeId && endpointCall.state != EndpointNodeState.NONE)
                    .count() + 1;
        }

        String proxyQuery = sparqlQueryService.injectOuterServices(query, endpointCallNode.getData());

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(endpointCall.queryNode.getData().endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(proxyQuery));

        headerMap.entrySet().stream()
                .filter(entry -> !(entry.getKey().equalsIgnoreCase("host") ||
                        entry.getKey().equalsIgnoreCase("content-length") ||
                        entry.getKey().equalsIgnoreCase("connection")))
                .forEach(entry -> requestBuilder.header(entry.getKey(), entry.getValue()));

        HttpRequest request = requestBuilder.build();
        saveRequest(request, queryId, endpointCall.nodeId);

        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        response.thenApply(HttpResponse::body)
                .thenAccept(respBody -> {
                    saveResponse(respBody, queryId, endpointCall.nodeId);
                })
                .exceptionally(e -> {
                    System.err.println("Error during request: " + e.getMessage());
                    return null;
                });
    }

    @Override
    public Optional<Node<EndpointCall>> getEndpointNode(Long queryId, Long nodeId) {
        Tree<EndpointCall> endpointCallTree = queryExecutionMap.get(queryId);
        if(endpointCallTree == null) {
            return Optional.empty();
        } else {
            return endpointCallTree.getRoot().findNode(endpointCall -> endpointCall.nodeId == nodeId);
        }
    }

    private void saveRequest(HttpRequest request, Long queryId, Long nodeId) {
        String fileName = composeFileName("request", queryId.toString(), nodeId.toString());

        try {
            File tempFile = File.createTempFile(fileName, ".tmp");
            tempFile.deleteOnExit();

            FileWriter fileWriter = new FileWriter(tempFile);
            fileWriter.write(HttpUtil.prettyPrintRequest(request));
            fileWriter.close();
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to write request to file.", e);
        }
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
