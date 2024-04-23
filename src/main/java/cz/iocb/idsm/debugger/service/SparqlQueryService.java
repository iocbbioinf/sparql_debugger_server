package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.model.Tree.Node;

import java.util.Optional;

public interface SparqlQueryService {

    Tree<SparqlQueryInfo> createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException;

    String injectOuterServices(String query, EndpointCall endpointCall);

    Optional<Node<SparqlQueryInfo>> getQueryInfoNode(Long queryId, Long subqueryId);

    String getEndpoint(Long endpointId);


}
