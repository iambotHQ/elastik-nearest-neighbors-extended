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

public class CreateModelRequest {
    public static class Source {
        public String _aknn_description;
        public int _aknn_nb_tables, _aknn_nb_bits_per_table;
        public Integer _aknn_nb_dimensions = null;

        public Source(String _aknn_description, int _aknn_nb_tables, int _aknn_nb_bits_per_table, Integer _aknn_nb_dimensions) {
            this._aknn_description = _aknn_description;
            this._aknn_nb_tables = _aknn_nb_tables;
            this._aknn_nb_bits_per_table = _aknn_nb_bits_per_table;
            this._aknn_nb_dimensions = _aknn_nb_dimensions;
        }

        public Source(String _aknn_description, int _aknn_nb_tables, int _aknn_nb_bits_per_table) {
            this._aknn_description = _aknn_description;
            this._aknn_nb_tables = _aknn_nb_tables;
            this._aknn_nb_bits_per_table = _aknn_nb_bits_per_table;
        }
    }

    public String _index, _type, _id;
    public Source _source;

    public CreateModelRequest(String _index, String _type, String _id, Source _source) {
        this._index = _index;
        this._type = _type;
        this._id = _id;
        this._source = _source;
    }
}
