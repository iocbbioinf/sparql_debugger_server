package cz.iocb.idsm.debugger.model;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SparqlQueryNode {
    public URL endpoint;
    public String query;
    public Long queryContextId;
    public Integer nodeId;
    public SparqlQueryNode parentNode;
    public List<SparqlQueryNode> children = new ArrayList<>();

    public SparqlQueryNode(URL endpoint, String query, Long queryContextId, Integer nodeId, SparqlQueryNode parentNode) {
        this.endpoint = endpoint;
        this.query = query;
        this.queryContextId = queryContextId;
        this.nodeId = nodeId;
        this.parentNode = parentNode;
    }
}
