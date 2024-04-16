package cz.iocb.idsm.debugger.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class SparqlQueryInfo {
    public URI endpoint;
    public String query;
    public Long queryContextId;
    public Long nodeId;

    public SparqlQueryInfo(URI endpoint, String query, Long queryContextId, Long nodeId) {
        this.endpoint = endpoint;
        this.query = query;
        this.queryContextId = queryContextId;
        this.nodeId = nodeId;
    }
}
