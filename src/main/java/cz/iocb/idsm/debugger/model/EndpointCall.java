package cz.iocb.idsm.debugger.model;

public class EndpointCall {
    public Long nodeId;
    public Long seqId;
    public SparqlQueryInfo queryNode;
    public Long startTime;
    public EndpointNodeState state = EndpointNodeState.NONE;

    public EndpointCall(Long nodeId, SparqlQueryInfo queryNode) {
        this.nodeId = nodeId;
        this.queryNode = queryNode;
    }
}
