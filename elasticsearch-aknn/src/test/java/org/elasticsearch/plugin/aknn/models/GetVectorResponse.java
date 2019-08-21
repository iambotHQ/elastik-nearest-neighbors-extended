package org.elasticsearch.plugin.aknn.models;

import java.util.List;

public class GetVectorResponse {
    public static class Source {
        public double[] _aknn_vector;

        public Source(double[] _aknn_vector) {
            this._aknn_vector = _aknn_vector;
        }
    }

    public String _id;
    public Source _source;

    public GetVectorResponse(String _id, Source _source) {
        this._id = _id;
        this._source = _source;
    }
}