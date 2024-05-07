package cz.iocb.idsm.debugger.model;

import java.net.URI;

public class SparqlQueryInfo {
    public final URI endpoint;
    public String query;
    public final Long queryContextId;
    public final Long nodeId;

    public SparqlQueryInfo(URI endpoint, String query, Long queryContextId, Long nodeId) {
        this.endpoint = endpoint;
        this.query = query;
        this.queryContextId = queryContextId;
        this.nodeId = nodeId;
    }
}
