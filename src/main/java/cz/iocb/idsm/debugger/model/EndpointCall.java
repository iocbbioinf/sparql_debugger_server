package cz.iocb.idsm.debugger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class EndpointCall {
    private Long queryId;
    private Long nodeId;
    private Long parentNodeId;
    private Long seqId;
    private Tree.Node<SparqlQueryInfo> queryNode;
    private Long startTime;
    private Long endTime;
    private String endpoint;
    private Long serviceCallId;
    private AtomicReference<Thread> callThread;
    private List<String> contentEncoding;
    private List<String> contentType;
    private String resultType;

    private Integer httpStatus;
    private EndpointNodeState state = EndpointNodeState.NONE;

    private Long resultsCount;

    private String charset;

    public EndpointCall(Long queryId, Long nodeId, Tree.Node<SparqlQueryInfo> queryNode, Long parentNodeId, String endpoint, Long serviceCallId) {
        this.queryId = queryId;
        this.nodeId = nodeId;
        this.queryNode = queryNode;
        this.parentNodeId = parentNodeId;
        this.endpoint = endpoint;
        this.serviceCallId = serviceCallId;
        this.callThread = new AtomicReference<>(null);

        this.contentEncoding = new ArrayList<>();
        this.contentType = new ArrayList<>();
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

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public EndpointNodeState getState() {
        return state;
    }

    public void setState(EndpointNodeState state) {
        this.state = state;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
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

    public Long getServiceCallId() {
        return serviceCallId;
    }

    public void setServiceCallId(Long serviceCallId) {
        this.serviceCallId = serviceCallId;
    }

    public List<String> getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(List<String> contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public List<String> getContentType() {
        return contentType;
    }

    public void setContentType(List<String> contentType) {
        this.contentType = contentType;
    }

    public Long getResultsCount() {
        return resultsCount;
    }

    public void setResultsCount(Long resultsCount) {
        this.resultsCount = resultsCount;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    @JsonIgnore
    public AtomicReference<Thread> getCallThread() {
        return callThread;
    }

    @JsonIgnore
    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCall that = (EndpointCall) o;
        return Objects.equals(queryId, that.queryId) && Objects.equals(nodeId, that.nodeId) && Objects.equals(parentNodeId, that.parentNodeId) && Objects.equals(seqId, that.seqId) && Objects.equals(queryNode, that.queryNode) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(endpoint, that.endpoint) && Objects.equals(serviceCallId, that.serviceCallId) && Objects.equals(httpStatus, that.httpStatus) && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, nodeId, parentNodeId, seqId, queryNode, startTime, endTime, endpoint, serviceCallId, httpStatus, state);
    }
}
