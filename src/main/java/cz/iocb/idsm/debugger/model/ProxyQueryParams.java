package cz.iocb.idsm.debugger.model;

public class ProxyQueryParams {
    private final Long queryId;
    private final Long parentId;
    private final Long subQueryId;
    private final Long serviceCallId;

    public ProxyQueryParams(Long queryId, Long parentId, Long subQueryId, Long serviceCallId) {
        this.queryId = queryId;
        this.parentId = parentId;
        this.subQueryId = subQueryId;
        this.serviceCallId = serviceCallId;
    }

    public Long getQueryId() {
        return queryId;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getSubQueryId() {
        return subQueryId;
    }

    public Long getServiceCallId() {
        return serviceCallId;
    }
}
