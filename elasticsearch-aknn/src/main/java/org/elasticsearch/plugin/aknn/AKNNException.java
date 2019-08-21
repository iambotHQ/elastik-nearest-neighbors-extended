package org.elasticsearch.plugin.aknn;

public class AKNNException extends RuntimeException {
    public AKNNException(Exception e) {
        super("AKNNException(" + e.getMessage() + ")", e);
    }
}
