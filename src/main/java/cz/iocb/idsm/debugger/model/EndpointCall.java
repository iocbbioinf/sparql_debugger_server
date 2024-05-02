package cz.iocb.idsm.debugger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class EndpointCall {
    private Long queryId;
    private Long nodeId;
    private Long parentNodeId;
    private Long seqId;
    private Tree.Node<SparqlQueryInfo> queryNode;
    private Long startTime;
    private String endpoint;
    private Long resultsCount;

    private Integer httpState;
    private EndpointNodeState state = EndpointNodeState.NONE;

    public EndpointCall(Long queryId, Long nodeId, Tree.Node<SparqlQueryInfo> queryNode, Long parentNodeId, String endpoint) {
        this.queryId = queryId;
        this.nodeId = nodeId;
        this.queryNode = queryNode;
        this.parentNodeId = parentNodeId;
        this.endpoint = endpoint;
    }

    public Long getQueryId() {
        return queryId;
    }

    public void setQueryId(Long queryId) {
        this.queryId = queryId;
    }

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    public Long getSeqId() {
        return seqId;
    }

    public void setSeqId(Long seqId) {
        this.seqId = seqId;
    }

    @JsonIgnore
    public Tree.Node<SparqlQueryInfo> getQueryNode() {
        return queryNode;
    }

    public void setQueryNode(Tree.Node<SparqlQueryInfo> queryNode) {
        this.queryNode = queryNode;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Integer getHttpState() {
        return httpState;
    }

    public void setHttpState(Integer httpState) {
        this.httpState = httpState;
    }

    public EndpointNodeState getState() {
        return state;
    }

    public void setState(EndpointNodeState state) {
        this.state = state;
    }

    public Long getParentNodeId() {
        return parentNodeId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCall that = (EndpointCall) o;
        return Objects.equals(queryId, that.queryId) && Objects.equals(nodeId, that.nodeId) && Objects.equals(parentNodeId, that.parentNodeId) && Objects.equals(seqId, that.seqId) && Objects.equals(queryNode, that.queryNode) && Objects.equals(startTime, that.startTime) && Objects.equals(endpoint, that.endpoint) && Objects.equals(resultsCount, that.resultsCount) && Objects.equals(httpState, that.httpState) && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, nodeId, parentNodeId, seqId, queryNode, startTime, endpoint, resultsCount, httpState, state);
    }
}
