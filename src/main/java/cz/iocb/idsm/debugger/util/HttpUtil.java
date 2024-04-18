package cz.iocb.idsm.debugger.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

public class HttpUtil {

    public static final String PARAM_QUERY_ID = "queryId";
    public static final String PARAM_PARENT_CALL_ID = "parentCallId";
    public static final String PARAM_SUBQUERY_ID = "subqueryId";
    public static final String PARAM_ENDPOINT = "endpoint";

    public static final String PARAM_QUERY = "query";
    public static final String PARAM_DEFAULT_GRAPH_URI = "default-graph-uri";
    public static final String PARAM_NAMED_GRAPH_URI = "named-graph-uri";

    public static final String HEADER_CONTENT_TYPE = "Content-Type";




    public static String prettyPrintRequest(HttpRequest request) {
        StringBuilder builder = new StringBuilder();

        // Print request method and URI
        builder.append("Request Method: ").append(request.method()).append("\n");
        builder.append("Request URI: ").append(request.uri()).append("\n");

        // Print headers
        HttpHeaders headers = request.headers();
        builder.append("Headers:\n");
        headers.map().forEach((k, v) -> builder.append("  ").append(k).append(": ").append(String.join(", ", v)).append("\n"));

        // Attempt to print the body if possible
        builder.append("Body:\n");
        request.bodyPublisher().ifPresentOrElse(
                bp -> bp.subscribe(new SimpleSubscriber(builder)),  // You will need to implement a SimpleSubscriber
                () -> builder.append("  No body present\n")
        );

        return URLDecoder.decode(builder.toString());
    }

    public static String prettyPrintResponse(String responseBody) {
        return responseBody;
    }

    // Simple Subscriber to read and print the body content
    static class SimpleSubscriber implements Flow.Subscriber<ByteBuffer> {
        private Flow.Subscription subscription;
        private final StringBuilder builder;

        public SimpleSubscriber(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            String bodyPart = StandardCharsets.UTF_8.decode(item).toString();
            builder.append("  ").append(bodyPart).append("\n");
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            builder.append("  Error reading body: ").append(throwable.getMessage()).append("\n");
        }

        @Override
        public void onComplete() {
            builder.append("  Body read complete.\n");
        }
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


}
