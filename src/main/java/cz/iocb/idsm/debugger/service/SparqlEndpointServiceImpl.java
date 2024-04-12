package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.EndpointNode;
import cz.iocb.idsm.debugger.model.ProxyQueryParams;
import cz.iocb.idsm.debugger.model.SparqlQueryNode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SparqlEndpointServiceImpl implements SparqlEndpointService{

    private Map<Long, EndpointNode> queryExecutionMap = new HashMap<>();

    @Override
    public void executeService(Long queryId, String query, SparqlQueryNode queryNode, EndpointNode parentNode) {

    }

    @Override
    public SparqlQueryNode createQuery(String query) {
        return null;
    }

    @Override
    public void executeQuery(String query, ProxyQueryParams proxyQueryParams) {

    }
}
