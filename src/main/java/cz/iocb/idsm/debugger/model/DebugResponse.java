package cz.iocb.idsm.debugger.model;

import java.io.InputStream;
import java.net.http.HttpResponse;

public class DebugResponse {

    HttpResponse<InputStream> httpResponse;
    InputStream respInputStream;


    public DebugResponse(HttpResponse<InputStream> httpResponse, InputStream respInputStream) {
        this.httpResponse = httpResponse;
        this.respInputStream = respInputStream;
    }

    public HttpResponse<InputStream> getHttpResponse() {
        return httpResponse;
    }

    public InputStream getRespInputStream() {
        return respInputStream;
    }
}
