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

public class CreateIndexRequest {
    public static class Source {
        public double[] _aknn_vector;
        public String extraData = null;

        public Source(double[] _aknn_vector) {
            this._aknn_vector = _aknn_vector;
        }

        public Source(double[] _aknn_vector, String extraData) {
            this._aknn_vector = _aknn_vector;
            this.extraData = extraData;
        }
    }

    public static class Doc {
        public String _id;
        public Source _source;

        public Doc(String _id, Source _source) {
            this._id = _id;
            this._source = _source;
        }
    }

    public String _index, _type, _aknn_uri;
    public List<Doc> _aknn_docs;

    public CreateIndexRequest(String _index, String _type, String _aknn_uri, List<Doc> _aknn_docs) {
        this._index = _index;
        this._type = _type;
        this._aknn_uri = _aknn_uri;
        this._aknn_docs = _aknn_docs;
    }
}
