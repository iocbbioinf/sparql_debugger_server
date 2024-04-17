package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.model.EndpointCall;
import cz.iocb.idsm.debugger.model.SparqlDebugException;
import cz.iocb.idsm.debugger.model.SparqlQueryInfo;
import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import cz.iocb.idsm.debugger.service.SparqlQueryService;
import cz.iocb.idsm.debugger.model.Tree.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cz.iocb.idsm.debugger.util.HttpUtil.*;
import static java.lang.String.format;

@RestController
public class DebuggerController {

    @Autowired
    private SparqlQueryService queryService;

    @Autowired
    private SparqlEndpointService endpointService;

    @PostMapping("/service")
    public void debugService(@RequestHeader Map<String, String> headerMap, @RequestBody String body,
                             @RequestParam(name = PARAM_QUERY_ID) Long queryId,
                             @RequestParam(name = PARAM_PARENT_CALL_ID) Long parentEndpointNodeId,
                             @RequestParam(name = PARAM_SUBQUERY_ID) Long subqueryId
                             ) {

        Node<SparqlQueryInfo> subqueryNode = queryService.getQueryInfoNode(queryId, subqueryId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %l", queryId)));

        Node<EndpointCall> parentEndpointNode = endpointService.getEndpointNode(queryId, parentEndpointNodeId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %l", queryId)));

        Node<EndpointCall> endpointCall = endpointService.createServiceEndpointNode(subqueryNode, parentEndpointNode);
        endpointService.callEndpoint(queryId, headerMap, body, endpointCall);
    }

    @PostMapping("/query")
    public void debugQuery(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "query") String query, @RequestParam(name = "endpoint") String endpoint) {
        Node<EndpointCall> endpointRoot = endpointService.createQueryEndpointRoot(endpoint, query);
        endpointService.callEndpoint(endpointRoot.getData().queryId, headerMap, query, endpointRoot);
    }

}
