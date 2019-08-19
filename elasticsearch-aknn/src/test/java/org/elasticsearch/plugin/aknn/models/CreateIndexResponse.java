package org.elasticsearch.plugin.aknn.models;

import java.util.List;

public class CreateIndexResponse {
    public static class Hit {
        String source, _id;
        double _score;

        public Hit(String source, String _id, double _score) {
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

    public CreateIndexResponse(HitInfo hits) {
        this.hits = hits;
    }
}
