package org.elasticsearch.plugin.aknn.models;

/**
 * {
 *   "_index":       "twitter_images",
 *   "_type":        "_doc",
 *   "_aknn_uri":    "aknn_models/aknn_model/twitter_image_search",
 *   "query_aknn": {
 *     "_aknn_vector": [1.0, 0.0, 0.0],
 *     "k1":1000,
 *     "k2":10
 *   }
 * }
 */

public class SimilaritySearchRequest {
    public static class Query {
        public double[] _aknn_vector;
        public int k1, k2;

        public Query(double[] _aknn_vector, int k1, int k2) {
            this._aknn_vector = _aknn_vector;
            this.k1 = k1;
            this.k2 = k2;
        }
    }

    public String _index, _type, _aknn_uri;
    public Query query_aknn;

    public SimilaritySearchRequest(String _index, String _type, String _aknn_uri, Query query_aknn) {
        this._index = _index;
        this._type = _type;
        this._aknn_uri = _aknn_uri;
        this.query_aknn = query_aknn;
    }
}
