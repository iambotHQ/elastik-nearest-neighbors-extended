package org.elasticsearch.plugin.aknn.models;

import java.util.List;
import java.util.Map;

public class SimilaritySearchResponse {
    public static class HitSource {
        public Map<String, Long> _aknn_hashes;

        public HitSource(Map<String, Long> _aknn_hashes) {
            this._aknn_hashes = _aknn_hashes;
        }
    }

    public static class Hit {
        public HitSource source;
        public String _id;
        public double _score;

        public Hit(HitSource source, String _id, double _score) {
            this.source = source;
            this._id = _id;
            this._score = _score;
        }
    }

    public static class HitInfo {
        public double max_score;
        public int total;
        public List<Hit> hits;

        public HitInfo(double max_score, int total, List<Hit> hits) {
            this.max_score = max_score;
            this.total = total;
            this.hits = hits;
        }
    }

    public HitInfo hits;

    public SimilaritySearchResponse(HitInfo hits) {
        this.hits = hits;
    }
}
