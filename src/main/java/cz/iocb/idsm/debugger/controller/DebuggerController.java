package cz.iocb.idsm.debugger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.model.Tree.Node;
import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import cz.iocb.idsm.debugger.service.SparqlQueryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static cz.iocb.idsm.debugger.model.FileId.FILE_TYPE.REQUEST;
import static cz.iocb.idsm.debugger.model.FileId.FILE_TYPE.RESPONSE;
import static cz.iocb.idsm.debugger.util.DebuggerUtil.pickFirstEntryIgnoreCase;
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

    @Autowired
    private SessionScopeQueryList sessionQueryList;

    @Resource(name = "sparqlRequestBean")
    SparqlRequest sparqlRequest;

    private static final Logger logger = LoggerFactory.getLogger(DebuggerController.class);

    @PostMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/serviceCall/{serviceCallId}/endpoint/{endpointId}")
    public void debugServicePost(@RequestHeader Map<String, String> headerMap,
                                 @PathVariable Long endpointId,
                                 @PathVariable Long queryId,
                                 @PathVariable Long parentEndpointNodeId,
                                 @PathVariable Long subqueryId,
                                 @PathVariable Long serviceCallId,
                                 @RequestParam(name = PARAM_QUERY, required = false) String query,
                                 @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                                 @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                                 @RequestBody(required = false) String body,
                                 HttpServletResponse response
    ) {

        SparqlRequestType sparqlRequestType = getRequestType(headerMap.get(HEADER_CONTENT_TYPE));

        sparqlRequest.setType(sparqlRequestType);
        if (sparqlRequestType.equals(SparqlRequestType.POST_FORM)) {
            sparqlRequest.setQuery(query);
        } else {
            sparqlRequest.setQuery(body);
        }
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        DebugResponse debugResponse = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId, serviceCallId);

        createResponse(debugResponse.getHttpResponse(), response, debugResponse.getRespInputStream());
    }

    @GetMapping("/service/query/{queryId}/parent/{parentEndpointNodeId}/subquery/{subqueryId}/serviceCall/{serviceCallId}/endpoint/{endpointId}")
    public void debugServiceGet(@RequestHeader Map<String, String> headerMap,
                                @PathVariable Long endpointId,
                                @PathVariable Long queryId,
                                @PathVariable Long parentEndpointNodeId,
                                @PathVariable Long subqueryId,
                                @PathVariable Long serviceCallId,
                                @RequestParam(name = PARAM_QUERY, required = false) String query,
                                @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                                @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                                HttpServletResponse response
    ) {

        sparqlRequest.setType(SparqlRequestType.GET);
        sparqlRequest.setQuery(query);
        sparqlRequest.setNamedGraphUri(namedGraphUri);
        sparqlRequest.setDefaultGraphUri(defaultGraphUri);
        sparqlRequest.setHeaderMap(headerMap);

        DebugResponse debugResponse = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId, serviceCallId);

        if(debugResponse!= null) {
            createResponse(debugResponse.getHttpResponse(), response, debugResponse.getRespInputStream());
        }
    }

    private void createResponse(HttpResponse<InputStream> httpResponse, HttpServletResponse response, InputStream respInputStream) {
        if (httpResponse == null) {
            return;
        }

        response.setStatus(httpResponse.statusCode());

        Map<String, List<String>> distinctResponseHeaders = pickFirstEntryIgnoreCase(httpResponse.headers().map());

        distinctResponseHeaders.entrySet().stream()
                .filter(entry -> !entry.getKey().toLowerCase().equals(":status"))
                .filter(entry -> !entry.getKey().toLowerCase().equals("transfer-encoding"))
                .forEach(entry -> {
                    String headerValue = String.join(",", entry.getValue());
                    response.addHeader(entry.getKey(), headerValue);
                });

        try {
            IOUtils.copy(respInputStream, response.getOutputStream());
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to red endpoint response body stream.", e);
        }
    }

    @PostMapping("/query/{queryId}/delete")
    public void deleteQuery(@PathVariable Long queryId) {
        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        endpointService.deleteQuery(queryId);
    }

    @GetMapping("/query")
    public Long debugQueryGet(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
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
//        newHeaderMap.put("accept", "application/sparql-results+xml, text/rdf n3, text/rdf ttl, text/rdf turtle, text/turtle, application/turtle, application/x-turtle, application/rdf xml, application/xml, application/sparql-results+json, text/csv, text/tab-separated-values, text/turtle, application/n-triples, application/ld+json");
        newHeaderMap.put("accept", headerMap.get("accept"));



        sparqlRequest.setHeaderMap(newHeaderMap);
//        sparqlRequest.setHeaderMap(headerMap);

        Long queryId = executeQuery(endpoint);

        sessionQueryList.add(queryId);

        return queryId;
    }

    @PostMapping("/query")
    public Long debugQueryPost(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                                     @RequestParam(name = PARAM_QUERY, required = false) String query,
                                     @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                                     @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                                     @RequestParam(name = PARAM_REQUEST_CONTEXT, required = false) String requestContext,
                                     @RequestBody(required = false) String body
    ) {

        logger.debug("debugQueryGet - start: headerMap: {}", Arrays.toString(headerMap.keySet().toArray()));

        populateSparqlRequest(requestContext, query, body);

        Long queryId = executeQuery(endpoint);

        sessionQueryList.add(queryId);

        return queryId;
    }

    @GetMapping("/syncquery")
    public Tree<EndpointCall> debugSyncQueryGet(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
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
//        newHeaderMap.put("accept", "application/sparql-results+xml, text/rdf n3, text/rdf ttl, text/rdf turtle, text/turtle, application/turtle, application/x-turtle, application/rdf xml, application/xml, application/sparql-results+json, text/csv, text/tab-separated-values, text/turtle, application/n-triples, application/ld+json");
        newHeaderMap.put("accept", headerMap.get("accept"));



        sparqlRequest.setHeaderMap(newHeaderMap);
//        sparqlRequest.setHeaderMap(headerMap);

        Tree<EndpointCall> executionTree = executeSyncQuery(endpoint);

        sessionQueryList.add(executionTree.getRoot().getData().getQueryId());

        return executionTree;
    }

    @PostMapping("/syncquery")
    public Tree<EndpointCall> debugSyncQueryPost(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                               @RequestParam(name = PARAM_QUERY, required = false) String query,
                               @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                               @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                               @RequestParam(name = PARAM_REQUEST_CONTEXT, required = false) String requestContext,
                               @RequestBody(required = false) String body
    ) {

        logger.debug("debugSyncQueryGet - start: headerMap: {}", Arrays.toString(headerMap.keySet().toArray()));

        populateSparqlRequest(requestContext, query, body);

        Tree<EndpointCall> executionTree = executeSyncQuery(endpoint);

        sessionQueryList.add(executionTree.getRoot().getData().getQueryId());

        return executionTree;
    }


    @GetMapping("/query/{queryId}/sse")
    public SseEmitter startSse(@PathVariable Long queryId) {
        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        if (endpointService.getQueryTree(queryId).isEmpty()) {
            throw new SparqlDebugException(format("Query doesn't exist. queryId=%d", queryId));
        }

        return endpointService.getQueryTree(queryId).get().getEmitter();
    }

    @GetMapping("/query/{queryId}")
    public Tree<EndpointCall> getQueryInfo(@PathVariable Long queryId) {

        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        if (endpointService.getQueryTree(queryId).isEmpty()) {
            throw new SparqlDebugException(format("Query doesn't exist. queryId=%d", queryId));
        }

        return endpointService.getQueryTree(queryId).get();

    }

    @GetMapping("/query/{queryId}/call/{callId}/request")
    public org.springframework.core.io.Resource getRequest(@PathVariable Long queryId, @PathVariable Long callId) {

        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        FileId fileId = new FileId(REQUEST, Long.valueOf(queryId), Long.valueOf(callId));
        return endpointService.getFile(fileId);
    }

    @GetMapping("/query/{queryId}/call/{callId}/response")
    public void getResponse(@PathVariable Long queryId, @PathVariable Long callId,
                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                            HttpServletResponse response) {

        if(!queryIsInSession(queryId)) {
            throw new SparqlDebugException("Query is not in current Web Session.");
        }

        FileId fileId = new FileId(RESPONSE, Long.valueOf(queryId), Long.valueOf(callId));

        EndpointCall endpointCall = endpointService.getEndpointNode(queryId, callId).get().getData();
        Boolean isCompressed = isCompressed(endpointCall.getContentEncoding());

        response.addHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, String.join(",", HttpHeaders.CONTENT_LENGTH.toLowerCase()));

        try {
            if (rangeHeader != null) {
                String[] ranges = rangeHeader.split("=")[1].split("-");
                int start = Integer.parseInt(ranges[0]);
                int end = Integer.parseInt(ranges[1]);

                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Accept-Ranges", "bytes");

                String rangeContent = readNBytesFromFile(endpointService.getFile(fileId).getInputStream(), start, end, isCompressed, StandardCharsets.UTF_8.displayName());

                response.getOutputStream().write(rangeContent.getBytes(StandardCharsets.UTF_8));

                return;
            }

            endpointService.getEndpointNode(queryId, callId).map(
                    endpointCallNode -> {
                        String contentEncoding = endpointCallNode.getData().getContentEncoding()
                                .stream().collect(Collectors.joining(","));
                        if (!contentEncoding.isEmpty()) {
                            response.addHeader(HttpHeaders.CONTENT_ENCODING, contentEncoding);
                        }

                        String contentType = endpointCallNode.getData().getContentType()
                                .stream().collect(Collectors.joining(","));
                        if (!contentType.isEmpty()) {
                            response.addHeader(HttpHeaders.CONTENT_TYPE, contentType);
                        }
                        return null;
                    }
            );

            IOUtils.copy(endpointService.getFile(fileId).getInputStream(), response.getOutputStream());

        } catch (IOException e) {
            throw new SparqlDebugException("Unable to create endpoint response body.", e);
        }
    }

    private Tree<EndpointCall> executeSyncQuery(String endpoint) {

        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException(format("Wrong IRI format: %s", endpoint), e);
        }

        Node<EndpointCall> endpointRoot = endpointService.createQueryEndpointRoot(endpointUri);

        HttpRequest request = endpointService.prepareEndpointToCall(endpointUri, endpointRoot.getData().getQueryId(), endpointRoot);
        endpointService.callEndpointSync(request, endpointUri, endpointRoot.getData().getQueryId(), endpointRoot);

        return endpointRoot.getTree();
    }

    private String readNBytesFromFile(InputStream inputStream, int begin, int end, Boolean isCompressed, String charset) {
        InputStream finalInputStream = new BufferedInputStream(inputStream);
        if (isCompressed) {
            try {
                finalInputStream = new GZIPInputStream(finalInputStream);
            } catch (IOException e) {
                throw new SparqlDebugException("Unable to read input stream.", e);
            }
        }

        return readNBytesImpl(finalInputStream, begin, end, charset);
    }

    private String readNBytesImpl(InputStream inputStream, int begin, int end, String charset) {
        try (inputStream) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[end - begin + 1];
            int bytesRead = inputStream.read(buffer, begin, end);

            if (bytesRead != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toString(charset);
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to read input stream.", e);
        }

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

    private DebugResponse executeService(Long endpointId, Long queryId, Long parentEndpointNodeId, Long subqueryId, Long serviceCallId) {
        logger.debug("executeService - start: queryId={}, parentEndpointNodeId={}, subqueryId={}, serviceCallId={}, endpointId={}",
                queryId, parentEndpointNodeId, subqueryId, serviceCallId, endpointId);

        if(endpointService.getCancellingQuerySet().contains(queryId)) {
            return null;
        }

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
        if (contentType == null) {
            throw new SparqlDebugException("Missing request header: content-type");
        } else {
            SparqlRequestType sparqlRequestType = SparqlRequestType.valueOfContentType(contentType);
            if (sparqlRequestType == null) {
                throw new SparqlDebugException("content-type request header has wrong value");
            }

            return sparqlRequestType;
        }
    }

    private Boolean queryIsInSession(Long queryId) {
        // TODO return sessionQueryList.contains(queryId);
        return true;
    }

    private void populateSparqlRequest(String requestContextStr, String query, String body) {
        if (requestContextStr != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                RequestContext requestContext = objectMapper.readValue(requestContextStr, RequestContext.class);
                logger.debug("Parsed RequestContext: {}", requestContext);

                if(requestContext.getMethod().equals("POST")) {
                    sparqlRequest.setType(SparqlRequestType.POST_FORM);
                } else {
                    sparqlRequest.setType(SparqlRequestType.GET);
                }

                if (sparqlRequest.getType() == SparqlRequestType.POST_FORM || sparqlRequest.getType() == SparqlRequestType.GET) {
                    sparqlRequest.setQuery(query);
                } else {
                    sparqlRequest.setQuery(body);
                }

                if(!requestContext.getNamedGraphs().isEmpty()) {
                    sparqlRequest.setNamedGraphUri(requestContext.getNamedGraphs().getFirst());
                }
                if(!requestContext.getDefaultGraphs().isEmpty()) {
                    sparqlRequest.setDefaultGraphUri((requestContext.getDefaultGraphs().getFirst()));
                }

                sparqlRequest.setHeaderMap(requestContext.getHeaders());

                sparqlRequest.getHeaderMap().put("accept", requestContext.getAcceptHeaderSelect());

                if(requestContext.getMethod().equals("POST")) {
                    sparqlRequest.getHeaderMap().put("content-type", "application/x-www-form-urlencoded");
                }

            } catch (Exception e) {
                logger.error("Failed to parse requestContext JSON", e);
                throw new IllegalArgumentException("Invalid JSON for requestContext");
            }
        }
    }
}

