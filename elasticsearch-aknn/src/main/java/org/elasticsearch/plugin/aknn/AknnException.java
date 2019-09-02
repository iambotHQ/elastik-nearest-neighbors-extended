package org.elasticsearch.plugin.aknn;

public class AknnException extends RuntimeException {
    public AknnException(Exception e) {
        super(e.getMessage(), e);
    }
}
