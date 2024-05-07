package cz.iocb.idsm.debugger.model;

public class ProxyQueryParams {
    private final Long queryId;
    private final Long parentId;
    private final Long subQueryId;

    public ProxyQueryParams(Long queryId, Long parentId, Long subQueryId) {
        this.queryId = queryId;
        this.parentId = parentId;
        this.subQueryId = subQueryId;
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
}
