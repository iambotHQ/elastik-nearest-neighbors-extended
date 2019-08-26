package org.elasticsearch.plugin.aknn.utils;

import com.google.gson.Gson;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.plugin.aknn.models.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class AknnAPI {
    RestClient restClient;
    private Gson gson = new Gson();

    public AknnAPI(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getResourceFileAsString(String fileName) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(fileName);
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
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
        restClient.performRequest(new Request("PUT", request._index));
        performJSONRequest(getResourceFileAsString("indexMapping.json"), request._index + "/" + request._type + "/_mapping", "PUT");
        performJSONRequest(gson.toJson(request), "_aknn_index");
    }

    public SimilaritySearchResponse similaritySearch(SimilaritySearchRequest request) throws IOException {
        Response response = performJSONRequest(gson.toJson(request), "_aknn_search_vec");
        return gson.fromJson(EntityUtils.toString(response.getEntity()), SimilaritySearchResponse.class);
    }

    public GetVectorResponse getVector(String _index, String _type, String docId) throws IOException {
        Response response = restClient.performRequest(new Request("GET", _index + "/" + _type + "/" + docId));
        return gson.fromJson(EntityUtils.toString(response.getEntity()), GetVectorResponse.class);
    }
}
