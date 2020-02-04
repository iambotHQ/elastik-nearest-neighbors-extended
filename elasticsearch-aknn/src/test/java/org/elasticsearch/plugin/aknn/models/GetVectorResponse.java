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

import java.util.Map;

public class GetVectorResponse {
    public static class Source {
        public double[] _aknn_vector;
        public Map<String, Long> _aknn_hashes;
        public String extraData;

        public Source(double[] _aknn_vector, Map<String, Long> _aknn_hashes, String extraData) {
            this._aknn_vector = _aknn_vector;
            this._aknn_hashes = _aknn_hashes;
            this.extraData = extraData;
        }
    }

    public String _id;
    public Source _source;

    public GetVectorResponse(String _id, Source _source) {
        this._id = _id;
        this._source = _source;
    }
}