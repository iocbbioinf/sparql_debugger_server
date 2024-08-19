package cz.iocb.idsm.debugger.model;


public enum SparqlResultType {
    JSON("json"), XML("xml"), CSV("csv"), HTML("html"), OTHER("other");

    public final String contentType;

    SparqlResultType(String contentType) {
        this.contentType = contentType;
    }

    public static SparqlResultType valueOfContentType(String contentType) {
        for (SparqlResultType e : values()) {
            if (contentType.contains(e.contentType)) {
                return e;
            }
        }
        return null;
    }
}
