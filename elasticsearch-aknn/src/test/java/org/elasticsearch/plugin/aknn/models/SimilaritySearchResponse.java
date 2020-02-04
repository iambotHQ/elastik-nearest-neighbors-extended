/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.elasticsearch.plugin.aknn.models;

import java.util.List;
import java.util.Map;

public class SimilaritySearchResponse {
    public static class HitSource {
        public Map<String, Long> _aknn_hashes;
        public String extraData;

        public HitSource(Map<String, Long> _aknn_hashes, String extraData) {
            this._aknn_hashes = _aknn_hashes;
            this.extraData = extraData;
        }
    }

    public static class Hit {
        public HitSource _source;
        public String _id;
        public double _score;

        public Hit(HitSource _source, String _id, double _score) {
            this._source = _source;
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
