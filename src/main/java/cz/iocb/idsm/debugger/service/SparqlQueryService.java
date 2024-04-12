package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.ProxyQueryParams;
import cz.iocb.idsm.debugger.model.SparqlDebugException;
import cz.iocb.idsm.debugger.model.SparqlQueryNode;

public interface SparqlQueryService {

    SparqlQueryNode createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException;

    String injectOuterServices(String endpoint, String query, ProxyQueryParams proxyQueryParams);

}
