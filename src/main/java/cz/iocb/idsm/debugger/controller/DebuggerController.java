package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import cz.iocb.idsm.debugger.service.SparqlEndpointServiceImpl;
import cz.iocb.idsm.debugger.service.SparqlQueryService;
import cz.iocb.idsm.debugger.model.Tree.Node;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import static cz.iocb.idsm.debugger.util.HttpUtil.*;
import static java.lang.String.format;

@RestController
public class DebuggerController {

    @Autowired
    private SparqlQueryService queryService;

    @Autowired
    private SparqlEndpointService endpointService;

    @Resource(name = "sparqlRequestBean")
    SparqlRequest sparqlRequest;

    private static final Logger logger = LoggerFactory.getLogger(DebuggerController.class);

    @PostMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/endpoint/{endpointId}")
    public void debugServicePost(@RequestHeader Map<String, String> headerMap,
                             @PathVariable Long endpointId,
                             @PathVariable Long queryId,
                             @PathVariable Long parentEndpointNodeId,
                             @PathVariable Long subqueryId,
                             @RequestParam(name = PARAM_QUERY, required = false) String query,
                             @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                             @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                             @RequestBody(required = false) String body
                             ) {

        SparqlRequestType sparqlRequestType = getRequestType(headerMap.get(HEADER_CONTENT_TYPE));

        sparqlRequest.setType(sparqlRequestType);
        if(sparqlRequestType.equals(SparqlRequestType.POST_FORM)) {
            sparqlRequest.setQuery(query);
        } else {
            sparqlRequest.setQuery(body);
        }
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        executeService(endpointId, queryId, parentEndpointNodeId, subqueryId);

    }

    @GetMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/endpoint/{endpointId}")
    public void debugServiceGet(@RequestHeader Map<String, String> headerMap,
                                @PathVariable Long endpointId,
                                @PathVariable Long queryId,
                                @PathVariable Long parentEndpointNodeId,
                                @PathVariable Long subqueryId,
                                @RequestParam(name = PARAM_QUERY, required = false) String query,
                                @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                                @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri
    ) {

        sparqlRequest.setType(SparqlRequestType.GET);
        sparqlRequest.setQuery(query);
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        executeService(endpointId, queryId, parentEndpointNodeId, subqueryId);
    }


    @GetMapping("query")
    public void debugQueryPost(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                               @RequestParam(name = PARAM_QUERY) String query,
                               @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                               @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri
                               ) {

        sparqlRequest.setType(SparqlRequestType.GET);
        sparqlRequest.setQuery(query);
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        executeQuery(endpoint);
    }

    @PostMapping("/query")
    public void debugQueryGet(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                              @RequestParam(name = PARAM_QUERY, required = false) String query,
                              @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                              @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                              @RequestBody(required = false) String body
    ) {

        logger.debug(format("debugQueryGet - start: headerMap: %s", Arrays.toString(headerMap.keySet().toArray())));

        SparqlRequestType sparqlRequestType = getRequestType(headerMap.get(HEADER_CONTENT_TYPE));

        sparqlRequest.setType(getRequestType(headerMap.get(HEADER_CONTENT_TYPE)));
        if(sparqlRequestType.equals(SparqlRequestType.POST_FORM)) {
            sparqlRequest.setQuery(query);
        } else {
            sparqlRequest.setQuery(body);
        }
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        executeQuery(endpoint);
    }

    private void executeQuery(String endpoint) {
        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException(format("Wrong IRI format: %s", endpoint), e);
        }

        Node<EndpointCall> endpointRoot = endpointService.createQueryEndpointRoot(endpointUri);

        endpointService.callEndpoint(endpointUri, endpointRoot.getData().queryId, endpointRoot);
    }

    private void executeService(Long endpointId, Long queryId, Long parentEndpointNodeId, Long subqueryId) {
        logger.debug(format("executeService - start: queryId=%s, parentEndpointNodeId=%s, subqueryId=%s, endpointId=%d",
                queryId, parentEndpointNodeId, subqueryId, endpointId));

        String endpoint = queryService.getEndpoint(endpointId);

        Node<SparqlQueryInfo> subqueryNode = queryService.getQueryInfoNode(queryId, subqueryId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %d", queryId)));

        Node<EndpointCall> parentEndpointNode = endpointService.getEndpointNode(queryId, parentEndpointNodeId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %d", queryId)));

        Node<EndpointCall> endpointCall = endpointService.createServiceEndpointNode(subqueryNode, parentEndpointNode);

        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException(format("Wrong IRI format: %s", endpoint), e);
        }

        endpointService.callEndpoint(endpointUri, queryId, endpointCall);

    }

    private SparqlRequestType getRequestType(String contentType) {
        if(contentType == null) {
            throw new SparqlDebugException("Missing request header: content-type");
        } else {
            SparqlRequestType sparqlRequestType = SparqlRequestType.valueOfContentType(contentType);
            if(sparqlRequestType == null) {
                throw new SparqlDebugException("content-type request header has wrong value");
            }

            return sparqlRequestType;
        }
    }

}
