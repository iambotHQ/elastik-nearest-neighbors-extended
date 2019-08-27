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

    public void createModel(CreateModelRequest request) throws IOException {
        performJSONRequest(gson.toJson(request), "_aknn_create_random");
    }

    public void createIndex(CreateIndexRequest request) throws IOException {
        performJSONRequest(gson.toJson(request), "_aknn_index?clear_cache=true");
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
