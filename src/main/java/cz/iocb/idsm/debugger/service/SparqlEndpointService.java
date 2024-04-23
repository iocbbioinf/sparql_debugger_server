package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.EndpointCall;
import cz.iocb.idsm.debugger.model.SparqlQueryInfo;
import cz.iocb.idsm.debugger.model.Tree.Node;


import java.net.URI;
import java.util.Map;
import java.util.Optional;

public interface SparqlEndpointService {
    Node<EndpointCall>  createServiceEndpointNode(Node<SparqlQueryInfo> queryNode, Node<EndpointCall> parentNode);
    Node<EndpointCall> createQueryEndpointRoot(URI endpoint);
    void callEndpoint(URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);
    Optional<Node<EndpointCall>> getEndpointNode(Long queryId, Long nodeId);
}
