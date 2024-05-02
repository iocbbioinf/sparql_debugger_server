package cz.iocb.idsm.debugger.model;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileId {
    private FILE_TYPE fileType;
    private Long queryId;
    private Long callId;

    public static final String TMP_DIR = "/tmp";

    public FileId(FILE_TYPE fileType, Long queryId, Long callId) {
        this.fileType = fileType;
        this.queryId = queryId;
        this.callId = callId;
    }

    public String getPath() {
        return TMP_DIR + "/" + getFileName();
    }

    public String getFileName() {
        return Stream.of(this.fileType.name(), this.queryId.toString(), this.callId.toString()).collect(Collectors.joining("_")) + ".tmp";
    }

    public enum FILE_TYPE {
        REQUEST, RESPONSE
    }

}
