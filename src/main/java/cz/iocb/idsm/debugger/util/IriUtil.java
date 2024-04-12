package cz.iocb.idsm.debugger.util;

public class IriUtil {

    public static String unwrapIri(String iri) {
        if (iri.startsWith("<") && iri.endsWith(">")) {
            return iri.substring(1, iri.length() - 1);
        } else {
            return iri;
        }
    }

    public static String wrapIri(String iri) {
        if (iri.startsWith("<") && iri.endsWith(">")) {
            return iri;
        } else {
            return "<" + iri + ">";
        }
    }

}
