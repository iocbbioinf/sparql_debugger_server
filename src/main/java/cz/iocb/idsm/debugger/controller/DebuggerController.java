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

import static java.lang.String.format;

@RestController
public class DebuggerController {

    @Autowired
    private SparqlQueryService queryService;

    @Autowired
    private SparqlEndpointService endpointService;

    @PostMapping("/service")
    public void debugService(@RequestHeader Map<String, String> headerMap, @RequestBody String body,
                             @RequestParam(name = "queryId") Long queryId,
                             @RequestParam(name = "parentId") Long parentEndpointNodeId,
                             @RequestParam(name = "subqueryId") Long subqueryId
                             ) {

        SparqlQueryInfo subquery = queryService.getQueryInfoNode(queryId, subqueryId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %l", queryId)))
                        .getData();

        Node<EndpointCall> parentEndpointNode = endpointService.getEndpointNode(queryId, parentEndpointNodeId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %l", queryId)));

        Node<EndpointCall> endpointCall = endpointService.createServiceEndpointNode(subquery, parentEndpointNode);
        endpointService.callEndpoint(headerMap, body, endpointCall);
    }

    @PostMapping("/query")
    public void debugQuery(@RequestHeader Map<String, String> headerMap, @RequestBody String body, @RequestParam(name = "endpoint") String endpoint) {
        Node<EndpointCall> endpointRoot = endpointService.createQueryEndpointRoot(endpoint, body);
        endpointService.callEndpoint(headerMap, body, endpointRoot);
    }

}
