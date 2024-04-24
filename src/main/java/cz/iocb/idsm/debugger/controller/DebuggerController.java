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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

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
    public ResponseEntity<String> debugServicePost(@RequestHeader Map<String, String> headerMap,
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

        HttpResponse<String> response = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId);

        return new ResponseEntity<>(response.body(), httpHeaders2MultiValueMap(response.headers()), response.statusCode());
    }

    @GetMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/endpoint/{endpointId}")
    public ResponseEntity<String> debugServiceGet(@RequestHeader Map<String, String> headerMap,
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

        HttpResponse<String> response = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId);

        return new ResponseEntity<>(response.body(), httpHeaders2MultiValueMap(response.headers()), response.statusCode());
    }


    @GetMapping("query")
    public HttpResponse<String> debugQueryPost(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                               @RequestParam(name = PARAM_QUERY) String query,
                               @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                               @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri
                               ) {

        sparqlRequest.setType(SparqlRequestType.GET);
        sparqlRequest.setQuery(query);
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        return executeQuery(endpoint);
    }

    @PostMapping("/query")
    public HttpResponse<String> debugQueryGet(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
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

        return executeQuery(endpoint);
    }

    @GetMapping("/query/{queryId}")
    public Tree<EndpointCall> getQueryInfo(@PathVariable Long queryId) {
        if(endpointService.getQueryTree(queryId).isEmpty()) {
            logger.error(format("Query doesn't exist. queryId=%d", queryId));
            throw new SparqlDebugException(format("Query doesn't exist. queryId=%d", queryId));
        }

        return endpointService.getQueryTree(queryId).get();

    }


    private HttpResponse<String> executeQuery(String endpoint) {
        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException(format("Wrong IRI format: %s", endpoint), e);
        }

        Node<EndpointCall> endpointRoot = endpointService.createQueryEndpointRoot(endpointUri);

        return endpointService.callEndpoint(endpointUri, endpointRoot.getData().getQueryId(), endpointRoot);

    }

    private HttpResponse<String> executeService(Long endpointId, Long queryId, Long parentEndpointNodeId, Long subqueryId) {
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

        return endpointService.callEndpoint(endpointUri, queryId, endpointCall);
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
