package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
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
    public Node<EndpointCall> createServiceEndpointNode(SparqlQueryInfo sparqlQueryInfo, Node<EndpointCall> parentNode) {
        EndpointCall endpointCall = new EndpointCall(endpointCallCounter.getAndAdd(1), sparqlQueryInfo);
        return parentNode.addNode(endpointCall, parentNode);
    }

    @Override
    public Node<EndpointCall> createQueryEndpointRoot(String endpoint, String query) {
        Long queryId = queryCounter.addAndGet(1);
        SparqlQueryInfo queryRoot = sparqlQueryService.createQueryTree(endpoint, query, queryId);

        EndpointCall endpointRoot = new EndpointCall(endpointCallCounter.getAndAdd(1), queryRoot);
        Tree<EndpointCall> queryTree = new Tree<>(endpointRoot);
        queryExecutionMap.put(queryId, queryTree);

        return queryTree.getRoot();
    }

    public void callEndpoint(Map<String, String> headerMap, String query, Node<EndpointCall> endpointCallNode) {

        EndpointCall endpointCall = endpointCallNode.getData();
        endpointCall.startTime = System.currentTimeMillis();
        endpointCall.state = EndpointNodeState.IN_PROGRESS;

        endpointCall.seqId = endpointCallNode.getParent().getChildren().stream()
                .filter(en -> en.getData().queryNode.nodeId == endpointCall.queryNode.nodeId && endpointCall.state != EndpointNodeState.NONE)
                .count() + 1;

        sparqlQueryService.injectOuterServices()

        sparqlQueryService.injectOuterServices(endpointCall.queryNode.endpoint.toString(), query, new ProxyQueryParams())


        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(endpointCall.queryNode.endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(query));

        headerMap.entrySet().stream()
                .filter(entry -> !(entry.getKey().equalsIgnoreCase("host") || entry.getKey().equalsIgnoreCase("content-length")))
                .forEach(entry -> requestBuilder.header(entry.getKey(), entry.getValue()));

        HttpRequest request = requestBuilder.build();

        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        response.thenApply(HttpResponse::body)
                .thenAccept(respBody -> {
                    Path tempFile;
                    try {
                        tempFile = Files.createTempFile("response", ".tmp");
                        Files.write(tempFile, respBody.getBytes(),
                                StandardOpenOption.CREATE);
                    } catch (IOException e) {
                        // Handle or log the IOException
                        throw new RuntimeException(e);
                    }
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
}
