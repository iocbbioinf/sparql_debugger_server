package cz.iocb.idsm.debugger.model;

public class SparqlDebugException extends RuntimeException{

    public SparqlDebugException() {
    }

    public SparqlDebugException(String message) {
        super(message);
    }

    public SparqlDebugException(String message, Throwable cause) {
        super(message, cause);
    }

    public SparqlDebugException(Throwable cause) {
        super(cause);
    }

    public SparqlDebugException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
