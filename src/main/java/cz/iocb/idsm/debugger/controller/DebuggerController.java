package cz.iocb.idsm.debugger.controller;

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

        HttpResponse<byte[]> httpResponse = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId, serviceCallId);

        createResponse(httpResponse, response);
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

        HttpResponse<byte[]> httpResponse = executeService(endpointId, queryId, parentEndpointNodeId, subqueryId, serviceCallId);

        createResponse(httpResponse, response);
    }

    private void createResponse(HttpResponse<byte[]> httpResponse, HttpServletResponse response) {
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

        byte[] responseBody = httpResponse.body();
        try {
            response.getOutputStream().write(responseBody);
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to red endpoint response body stream.", e);
        }
    }

    @PostMapping("/query/{queryId}/cancel")
    public void cancelQuery(@PathVariable Long queryId) {
        endpointService.cancelQuery(queryId);
    }

    @PostMapping("/query/{queryId}/delete")
    public void deleteQuery(@PathVariable Long queryId) {
        endpointService.deleteQuery(queryId);
    }

    @GetMapping("/query")
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
    public Long debugQueryPost(@RequestHeader Map<String, String> headerMap, @RequestParam(name = "endpoint") String endpoint,
                                     @RequestParam(name = PARAM_QUERY, required = false) String query,
                                     @RequestParam(name = PARAM_NAMED_GRAPH_URI, required = false) String namedGraphUri,
                                     @RequestParam(name = PARAM_DEFAULT_GRAPH_URI, required = false) String defaultGraphUri,
                                     @RequestBody(required = false) String body
    ) {

        logger.debug("debugQueryGet - start: headerMap: {}", Arrays.toString(headerMap.keySet().toArray()));

        SparqlRequestType sparqlRequestType = getRequestType(headerMap.get(HEADER_CONTENT_TYPE));

        sparqlRequest.setType(getRequestType(headerMap.get(HEADER_CONTENT_TYPE)));
        if (sparqlRequestType.equals(SparqlRequestType.POST_FORM)) {
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

        return queryId;
    }

    @GetMapping("/query/{queryId}/sse")
    public SseEmitter startSse(@PathVariable Long queryId) {
        if (endpointService.getQueryTree(queryId).isEmpty()) {
            logger.error("Query doesn't exist. queryId={}", queryId);
            throw new SparqlDebugException(format("Query doesn't exist. queryId=%d", queryId));
        }

        return endpointService.getQueryTree(queryId).get().getEmitter();
    }



    @GetMapping("/query/{queryId}")
    public Tree<EndpointCall> getQueryInfo(@PathVariable Long queryId) {
        if (endpointService.getQueryTree(queryId).isEmpty()) {
            logger.error("Query doesn't exist. queryId={}", queryId);
            throw new SparqlDebugException(format("Query doesn't exist. queryId=%d", queryId));
        }

        return endpointService.getQueryTree(queryId).get();

    }

    @GetMapping("/query/{queryId}/call/{callId}/request")
    public org.springframework.core.io.Resource getRequest(@PathVariable Long queryId, @PathVariable Long callId) {


        FileId fileId = new FileId(REQUEST, Long.valueOf(queryId), Long.valueOf(callId));
        return endpointService.getFile(fileId);
    }

    @GetMapping("/query/{queryId}/call/{callId}/response")
    public void getResponse(@PathVariable Long queryId, @PathVariable Long callId,
                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                            HttpServletResponse response) {

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

                String rangeContent = readNBytesFromFile(endpointService.getFile(fileId).getInputStream(), start, end, isCompressed);

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

    private String readNBytesFromFile(InputStream inputStream, int begin, int end, Boolean isCompressed) {
        InputStream finalInputStream = new BufferedInputStream(inputStream);
        if (isCompressed) {
            try {
                finalInputStream = new GZIPInputStream(finalInputStream);
            } catch (IOException e) {
                throw new SparqlDebugException("Unable to read input stream.", e);
            }
        }

        return readNBytesImpl(finalInputStream, begin, end);
    }

    private String readNBytesImpl(InputStream inputStream, int begin, int end) {
        try (inputStream) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[end - begin + 1];
            int bytesRead = inputStream.read(buffer, begin, end);

            if (bytesRead != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toString("UTF-8");
        } catch (IOException e) {
            throw new SparqlDebugException("Unable to read input stream.", e);
        }

    }


    private byte[] compress(final String str) throws IOException {
        if ((str == null) || (str.length() == 0)) {
            return null;
        }
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(obj);
        gzip.write(str.getBytes("UTF-8"));
        gzip.flush();
        gzip.close();
        return obj.toByteArray();
    }

    public static byte[] decompress(final byte[] compressed) throws IOException {
        if ((compressed == null) || (compressed.length == 0)) {
            return new byte[]{};
        }
        final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));

        return gis.readAllBytes();
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


    private HttpResponse<byte[]> executeService(Long endpointId, Long queryId, Long parentEndpointNodeId, Long subqueryId, Long serviceCallId) {
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

}
