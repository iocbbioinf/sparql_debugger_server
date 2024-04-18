package cz.iocb.idsm.debugger.model;

public enum SparqlRequestType {
    GET("none"), POST_FORM("application/x-www-form-urlencoded"), POST_PLAIN("application/sparql-query");

    public final String contentType;

    SparqlRequestType(String contentType) {
        this.contentType = contentType;
    }

    public static SparqlRequestType valueOfLabel(String contentType) {
        for (SparqlRequestType e : values()) {
            if (e.contentType.equals(contentType)) {
                return e;
            }
        }
        return null;
    }
}
