package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.model.Tree.Node;
import org.springframework.core.io.FileSystemResource;


import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

public interface SparqlEndpointService {
    Node<EndpointCall>  createServiceEndpointNode(String endpoint, Node<SparqlQueryInfo> queryNode, Node<EndpointCall> parentNode, Long serviceCallId);
    Node<EndpointCall> createQueryEndpointRoot(URI endpoint);

    HttpRequest prepareEndpointToCall(URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);
    HttpResponse<byte[]> callEndpointSync(HttpRequest request, URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);
    void callEndpointAsync(HttpRequest request, URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);

    Optional<Node<EndpointCall>> getEndpointNode(Long queryId, Long nodeId);

    Optional<Tree<EndpointCall>> getQueryTree(Long queryId);

    FileSystemResource getFile(FileId fileId);

    Long getResultCount(InputStream resultStream, SparqlResultType resultType, List<String> contentEncoding);

    void deleteQuery(Long queryId);

    void deleteQueries(Long cutoff);
}

