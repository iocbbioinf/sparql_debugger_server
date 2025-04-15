package cz.iocb.idsm.debugger.util;

import cz.iocb.idsm.debugger.service.SparqlEndpointServiceImpl;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.zip.GZIPInputStream;

public class HttpUtil {

    public static final String PATH_QUERY_ID = "query";
    public static final String PATH_PARENT_CALL_ID = "parent";
    public static final String PATH_SUBQUERY_ID = "subquery";
    public static final String PATH_SERVICE_CALL_ID = "serviceCall";
    public static final String PATH_ENDPOINT = "endpoint";

    public static final String PARAM_QUERY = "query";
    public static final String PARAM_DEFAULT_GRAPH_URI = "default-graph-uri";
    public static final String PARAM_NAMED_GRAPH_URI = "named-graph-uri";
    public static final String PARAM_REQUEST_CONTEXT = "requestcontext";
    public static final String SSE_ENABLED = "sseEnabled";

    public static final String HEADER_CONTENT_TYPE = "content-type";

    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);


    public static String prettyPrintRequest(HttpRequest request, String queryStr) {
        StringBuilder builder = new StringBuilder();


        builder.append("Query:\n");
        String prettyQueryStr = queryStr;
        try {
            prettyQueryStr = OpAsQuery.asQuery(Algebra.compile(QueryFactory.create(queryStr))).serialize();
        } catch (Exception e) {
            logger.error("Unable to format SPARQL request.", e);
        }

        builder.append(prettyQueryStr);

        builder.append("Request Method: ").append(request.method()).append("\n");
        builder.append("Request URL: ").append(request.uri()).append("\n");

        HttpHeaders headers = request.headers();
        builder.append("Headers:\n");
        headers.map().forEach((k, v) -> builder.append("  ").append(k).append(": ").append(String.join(", ", v)).append("\n"));


//        return URLDecoder.decode(builder.toString());
        return builder.toString();
    }

    public static String prettyPrintResponse(String responseBody) {
        return responseBody;
    }

    public static URI addQueryParam(URI uri, String... queryParams) throws URISyntaxException {

        String newQuery = uri.getQuery();

        for(String queryParam: queryParams) {
            if (newQuery == null) {
                newQuery = queryParam;
            } else {
                newQuery += "&" + queryParam;
            }
        }

        return new URI(uri.getScheme(), uri.getAuthority(),
                uri.getPath(), newQuery, uri.getFragment());
    }

    public static MultiValueMap<String, String> httpHeaders2MultiValueMap(HttpHeaders headers) {
        //TODO
        /*
        MultiValueMap<String, String> resultMap = new LinkedMultiValueMap<>();
        headers.map().entrySet().stream()
                .filter(entry -> !entry.getKey().toLowerCase().equals("transfer-encoding"))
                .forEach(entry -> entry.getValue().stream()
                        .forEach(str -> resultMap.add(entry.getKey(), str)));
         */

        MultiValueMap<String, String> resultMap = new LinkedMultiValueMap<>();
        headers.map().entrySet().stream()
                .filter(entry -> !entry.getKey().toLowerCase().equals(":status"))
                .forEach(entry -> entry.getValue().stream()
                        .forEach(str -> resultMap.add(entry.getKey(), str)));

        return resultMap;
    }

    public static Boolean isCompressed(List<String> contentEncoding) {
        return contentEncoding.stream()
                .filter(value -> value.equalsIgnoreCase("gzip"))
                .findAny()
                .map(value -> true)
                .orElse(false);
    }


}
