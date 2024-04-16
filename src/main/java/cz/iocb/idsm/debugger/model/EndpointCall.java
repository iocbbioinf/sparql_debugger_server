package cz.iocb.idsm.debugger.model;

public class EndpointCall {
    public Long queryId;
    public Long nodeId;
    public Long seqId;
    public Tree.Node<SparqlQueryInfo> queryNode;
    public Long startTime;
    public EndpointNodeState state = EndpointNodeState.NONE;

    public EndpointCall(Long queryId, Long nodeId, Tree.Node<SparqlQueryInfo> queryNode) {
        this.queryId = queryId;
        this.nodeId = nodeId;
        this.queryNode = queryNode;
    }
}
