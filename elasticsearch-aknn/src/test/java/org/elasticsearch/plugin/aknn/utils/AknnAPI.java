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
package org.elasticsearch.plugin.aknn.utils;

import com.google.gson.Gson;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.plugin.aknn.models.*;
import java.io.IOException;
import java.util.Optional;

public class AknnAPI {
    RestClient restClient;
    private Gson gson = new Gson();

    public AknnAPI(RestClient restClient) {
        this.restClient = restClient;
    }

    public Response performJSONRequest(String json, String endpoint, String method) throws IOException {
        Request postReq = new Request(method, endpoint);
        postReq.setEntity(new StringEntity(
                json,
                ContentType.APPLICATION_JSON));
        return restClient.performRequest(postReq);
    }

    public Response performJSONRequest(String json, String endpoint) throws IOException {
        return performJSONRequest(json, endpoint, "POST");
    }

    public Response createModel(CreateModelRequest request) throws IOException {
        return performJSONRequest(gson.toJson(request), "_aknn_create_random");
    }

    public void createIndex(CreateIndexRequest request) throws IOException {
        restClient.performRequest(new Request("GET", "_aknn_clear_cache"));
        performJSONRequest(gson.toJson(request), "_aknn_index");
    }

    public SimilaritySearchResponse similaritySearch(SimilaritySearchRequest request, boolean orderDesc) throws IOException {
        Response response = performJSONRequest(gson.toJson(request), "_aknn_search_vec?debug=true&order=" + (orderDesc ? "desc" : "asc"));
        return gson.fromJson(EntityUtils.toString(response.getEntity()), SimilaritySearchResponse.class);
    }

    public SimilaritySearchResponse similaritySearch(SimilaritySearchRequest request) throws IOException {
        Response response = performJSONRequest(gson.toJson(request), "_aknn_search_vec?debug=true");
        return gson.fromJson(EntityUtils.toString(response.getEntity()), SimilaritySearchResponse.class);
    }

    public GetVectorResponse getVector(String _index, String _type, String docId) throws IOException {
        Response response = restClient.performRequest(new Request("GET", _index + "/" + _type + "/" + docId));
        return gson.fromJson(EntityUtils.toString(response.getEntity()), GetVectorResponse.class);
    }
}
