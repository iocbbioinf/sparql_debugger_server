package cz.iocb.idsm.debugger.model;

import java.util.Map;
import java.util.Objects;

public class SparqlRequest {
    private SparqlRequestType type;
    private String query;
    private String defaultGraphUri;
    private String namedGraphUri;
    private Map<String, String> headerMap;

    public SparqlRequest() {
    }

    public SparqlRequestType getType() {
        return type;
    }

    public void setType(SparqlRequestType type) {
        this.type = type;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getDefaultGraphUri() {
        return defaultGraphUri;
    }

    public void setDefaultGraphUri(String defaultGraphUri) {
        this.defaultGraphUri = defaultGraphUri;
    }

    public String getNamedGraphUri() {
        return namedGraphUri;
    }

    public void setNamedGraphUri(String namedGraphUri) {
        this.namedGraphUri = namedGraphUri;
    }

    public Map<String, String> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, String> headerMap) {
        this.headerMap = headerMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SparqlRequest that = (SparqlRequest) o;
        return type == that.type && Objects.equals(query, that.query) && Objects.equals(defaultGraphUri, that.defaultGraphUri) && Objects.equals(namedGraphUri, that.namedGraphUri) && Objects.equals(headerMap, that.headerMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, query, defaultGraphUri, namedGraphUri, headerMap);
    }
}

