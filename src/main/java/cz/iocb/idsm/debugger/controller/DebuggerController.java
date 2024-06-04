package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import cz.iocb.idsm.debugger.service.SparqlQueryService;
import cz.iocb.idsm.debugger.model.Tree.Node;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static cz.iocb.idsm.debugger.model.FileId.FILE_TYPE.REQUEST;
import static cz.iocb.idsm.debugger.model.FileId.FILE_TYPE.RESPONSE;
import static cz.iocb.idsm.debugger.util.HttpUtil.*;
import static java.lang.String.format;

@RestController
public class DebuggerController {

    @Autowired
    private SparqlQueryService queryService;

    @Autowired
    private SparqlEndpointService endpointService;

    @Autowired
    private SparqlQueryService sparqlQueryService;


    @Resource(name = "sparqlRequestBean")
    SparqlRequest sparqlRequest;

    private static final Logger logger = LoggerFactory.getLogger(DebuggerController.class);

    @PostMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/serviceCall/{serviceCallId}/endpoint/{endpointId}")
    public ResponseEntity<String> debugServicePost(@RequestHeader Map<String, String> headerMap,
                             @PathVariable Long endpointId,
                             @PathVariable Long queryId,
                             @PathVariable Long parentEndpointNodeId,
                             @PathVariable Long subqueryId,
                             @PathVariable Long serviceCallId,
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

        HttpResponse<String> response = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId, serviceCallId);

        return new ResponseEntity<>(response.body(), httpHeaders2MultiValueMap(response.headers()), response.statusCode());
    }

    @GetMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/serviceCall/{serviceCallId}/endpoint/{endpointId}")
    public ResponseEntity<String> debugServiceGet(@RequestHeader Map<String, String> headerMap,
                                                  @PathVariable Long endpointId,
                                                  @PathVariable Long queryId,
                                                  @PathVariable Long parentEndpointNodeId,
                                                  @PathVariable Long subqueryId,
                                                  @PathVariable Long serviceCallId,
                                                  @RequestParam(name = PARAM_QUERY, required = false) String query,
                                                  @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                                                  @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri
    ) {

        sparqlRequest.setType(SparqlRequestType.GET);
        sparqlRequest.setQuery(query);
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        HttpResponse<String> response = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId, serviceCallId);

        return new ResponseEntity<>(response.body(), httpHeaders2MultiValueMap(response.headers()), response.statusCode());
    }

    @PostMapping("query/{queryId}/cancel")
    public void cancelQuery(@PathVariable Long queryId) {
        //endpointService.cancelQuery()
    }


    @GetMapping("query")
    public SseEmitter debugQueryGet(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                               @RequestParam(name = PARAM_QUERY) String query,
                               @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                               @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri
                               ) {

        sparqlRequest.setType(SparqlRequestType.POST_FORM);
        sparqlRequest.setQuery(query);
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);

        Map<String, String> newHeaderMap = new HashMap<>();
        newHeaderMap.put("content-type", "application/x-www-form-urlencoded");
        newHeaderMap.put("accept", "application/sparql-results+xml, text/rdf n3, text/rdf ttl, text/rdf turtle, text/turtle, application/turtle, application/x-turtle, application/rdf xml, application/xml, application/sparql-results+json, text/csv, text/tab-separated-values, text/turtle, application/n-triples, application/ld+json");

        sparqlRequest.setHeaderMap(newHeaderMap);
//        sparqlRequest.setHeaderMap(headerMap);

        Long queryId = executeQuery(endpoint);

        SseEmitter result = endpointService.getQueryTree(queryId).get().getEmitter();

        return result;
    }

    @PostMapping("/query")
    public SseEmitter debugQueryPost(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                              @RequestParam(name = PARAM_QUERY, required = false) String query,
                              @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                              @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                              @RequestBody(required = false) String body
    ) {

        logger.debug("debugQueryGet - start: headerMap: {}", Arrays.toString(headerMap.keySet().toArray()));

        SparqlRequestType sparqlRequestType = getRequestType(headerMap.get(HEADER_CONTENT_TYPE));

        sparqlRequest.setType(getRequestType(headerMap.get(HEADER_CONTENT_TYPE)));
        if(sparqlRequestType.equals(SparqlRequestType.POST_FORM)) {
            sparqlRequest.setQuery(query);
        } else {
            sparqlRequest.setQuery(body);
        }
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);

        Map<String, String> newHeaderMap = new HashMap<>();
        newHeaderMap.put("content-type", "application/x-www-form-urlencoded");

        sparqlRequest.setHeaderMap(newHeaderMap);
//        sparqlRequest.setHeaderMap(headerMap);


        Long queryId = executeQuery(endpoint);

        return endpointService.getQueryTree(queryId).get().getEmitter();
    }


    @GetMapping("/query/{queryId}")
    public Tree<EndpointCall> getQueryInfo(@PathVariable Long queryId) {
        if(endpointService.getQueryTree(queryId).isEmpty()) {
            logger.error(format("Query doesn't exist. queryId=%d", queryId));
            throw new SparqlDebugException(format("Query doesn't exist. queryId=%d", queryId));
        }

        return endpointService.getQueryTree(queryId).get();

    }

    @GetMapping("/query/{queryId}/call/{callId}/request")
    public org.springframework.core.io.Resource getRequest(@PathVariable String queryId, @PathVariable String callId) {
        FileId fileId = new FileId(REQUEST, Long.valueOf(queryId), Long.valueOf(callId));
        return endpointService.getFile(fileId);
    }

    @GetMapping("/query/{queryId}/call/{callId}/response")
    public org.springframework.core.io.Resource getResponse(@PathVariable String queryId, @PathVariable String callId) {
        FileId fileId = new FileId(RESPONSE, Long.valueOf(queryId), Long.valueOf(callId));
        return endpointService.getFile(fileId);
    }

    private Long executeQuery(String endpoint) {
        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException(format("Wrong IRI format: %s", endpoint), e);
        }

        Node<EndpointCall> endpointRoot = endpointService.createQueryEndpointRoot(endpointUri);

        HttpRequest request = endpointService.prepareEndpointToCall(endpointUri, endpointRoot.getData().getQueryId(), endpointRoot);
        endpointService.callEndpointAsync(request, endpointUri, endpointRoot.getData().getQueryId(), endpointRoot);

        return endpointRoot.getData().getQueryId();
    }



    private HttpResponse<String> executeService(Long endpointId, Long queryId, Long parentEndpointNodeId, Long subqueryId, Long serviceCallId) {
        logger.debug("executeService - start: queryId={}, parentEndpointNodeId={}, subqueryId={}, serviceCallId={}, endpointId={}",
                queryId, parentEndpointNodeId, subqueryId, serviceCallId, endpointId);

        String endpoint = queryService.getEndpoint(endpointId);

        Optional<Node<SparqlQueryInfo>> subqueryNode = queryService.getQueryInfoNode(queryId, subqueryId);

        Node<EndpointCall> parentEndpointNode = endpointService.getEndpointNode(queryId, parentEndpointNodeId)
                .orElseThrow(() -> new SparqlDebugException(format("queryId param value is not valid. querId: %d", queryId)));

        Node<EndpointCall> endpointCall = endpointService.createServiceEndpointNode(endpoint, subqueryNode.orElseGet(null), parentEndpointNode, serviceCallId);

        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException(format("Wrong IRI format: %s", endpoint), e);
        }

        HttpRequest request = endpointService.prepareEndpointToCall(endpointUri, queryId, endpointCall);
        return endpointService.callEndpointSync(request, endpointUri, queryId, endpointCall);
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
