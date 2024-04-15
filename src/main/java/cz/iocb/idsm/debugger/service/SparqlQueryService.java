package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.ProxyQueryParams;
import cz.iocb.idsm.debugger.model.SparqlDebugException;
import cz.iocb.idsm.debugger.model.SparqlQueryInfo;
import cz.iocb.idsm.debugger.model.Tree.Node;

import java.util.Optional;

public interface SparqlQueryService {

    SparqlQueryInfo createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException;

    String injectOuterServices(String endpoint, String query, ProxyQueryParams proxyQueryParams);

    Optional<Node<SparqlQueryInfo>> getQueryInfoNode(Long queryId, Long subqueryId);

}
