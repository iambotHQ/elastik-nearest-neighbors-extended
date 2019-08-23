package org.elasticsearch.plugin.aknn;

public class AknnException extends RuntimeException {
    public AknnException(Exception e) {
        super("AknnException(" + e.getMessage() + ")", e);
    }
}
