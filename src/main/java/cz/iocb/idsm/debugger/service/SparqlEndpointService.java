package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.EndpointNode;
import cz.iocb.idsm.debugger.model.ProxyQueryParams;
import cz.iocb.idsm.debugger.model.SparqlQueryNode;

public interface SparqlEndpointService {
    void executeService(Long queryId, String query, SparqlQueryNode queryNode, EndpointNode parentNode);
    SparqlQueryNode createQuery(String query);
    void executeQuery(String query, ProxyQueryParams proxyQueryParams);
}
