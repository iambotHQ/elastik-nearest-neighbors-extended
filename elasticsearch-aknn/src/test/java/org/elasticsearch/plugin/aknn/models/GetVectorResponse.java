package org.elasticsearch.plugin.aknn.models;

import java.util.Map;

public class GetVectorResponse {
    public static class Source {
        public double[] _aknn_vector;
        public Map<String, Long> _aknn_hashes;

        public Source(double[] _aknn_vector, Map<String, Long> _aknn_hashes) {
            this._aknn_vector = _aknn_vector;
            this._aknn_hashes = _aknn_hashes;
        }
    }

    public String _id;
    public Source _source;

    public GetVectorResponse(String _id, Source _source) {
        this._id = _id;
        this._source = _source;
    }
}