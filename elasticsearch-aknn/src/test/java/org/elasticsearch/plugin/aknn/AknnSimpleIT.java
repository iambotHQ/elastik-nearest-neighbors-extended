/*
 * Copyright [2018] [Alex Klibisz]
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

package org.elasticsearch.plugin.aknn;

import com.google.gson.Gson;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.plugin.aknn.models.CreateIndexResponse;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class AknnSimpleIT extends ESIntegTestCase {

    private Client client;
    private RestClient restClient;
    private Gson gson = new Gson();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        client = client();
        restClient = getRestClient();
    }

    public String getResourceFileAsString(String fileName) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(fileName);
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    private Response performJSONRequest(String jsonPath, String endpoint, String method) throws IOException {
        Request postReq = new Request(method, endpoint);
        postReq.setEntity(new StringEntity(
                getResourceFileAsString(jsonPath),
                ContentType.APPLICATION_JSON));
        return restClient.performRequest(postReq);
    }

    private Response performJSONRequest(String jsonPath, String endpoint) throws IOException {
        return performJSONRequest(jsonPath, endpoint, "POST");
    }

    private void prepareData() throws IOException {
        performJSONRequest("createModel.json", "_aknn_create");
        // create index & mapping
        restClient.performRequest(new Request("PUT", "twitter_images"));
        performJSONRequest("indexMapping.json", "twitter_images/_doc/_mapping", "PUT");
        // fill index with data
        performJSONRequest("createIndex.json", "_aknn_index");
        refresh("twitter_images", "aknn_models");
    }


    /**
     * Test that the plugin was installed correctly by hitting the _cat/plugins endpoint.
     * @throws IOException if performing a request fails
     */
    public void testPluginInstallation() throws IOException {
        Response response = restClient.performRequest(new Request("GET", "_cat/plugins"));
        String body = EntityUtils.toString(response.getEntity());
        assertTrue(body.contains("elasticsearch-aknn"));
    }

    /**
     * Test that search results returned by _aknn_search_vec and _aknn_search don't differ
     * @throws IOException if performing a request fails
     */
    public void testSearchResultsTheSame() throws IOException {
        prepareData();

        Response response = performJSONRequest("similaritySearch.json", "_aknn_search_vec");
        CreateIndexResponse createIndexResponse = gson.fromJson(EntityUtils.toString(response.getEntity()), CreateIndexResponse.class);
        assertNotNull(createIndexResponse.hits);
        assertNotNull(createIndexResponse.hits.hits);
        assertEquals(4, createIndexResponse.hits.hits.size());

        response = restClient.performRequest(new Request("GET", "twitter_images/_doc/1/_aknn_search?k1=1000&k2=10"));
        CreateIndexResponse createIndexResponseIndexed = gson.fromJson(EntityUtils.toString(response.getEntity()), CreateIndexResponse.class);
        assertNotNull(createIndexResponseIndexed.hits);
        assertNotNull(createIndexResponseIndexed.hits.hits);
        assertEquals(4, createIndexResponseIndexed.hits.hits.size());

        for(int i = 0; i < createIndexResponse.hits.hits.size(); i++) {
            assertEquals(createIndexResponse.hits.hits.get(i)._score, createIndexResponseIndexed.hits.hits.get(i)._score, 0.001);
        }
    }

    /**
     * Test that search results returned by _aknn_search_vec are in correct order
     * @throws IOException if performing a request fails
     */
    public void testSearchResultsOrder() throws IOException {
        prepareData();

        Response response = performJSONRequest("similaritySearch.json", "_aknn_search_vec");
        CreateIndexResponse createIndexResponse = gson.fromJson(EntityUtils.toString(response.getEntity()), CreateIndexResponse.class);
        assertNotNull(createIndexResponse.hits);
        assertNotNull(createIndexResponse.hits.hits);
        assertEquals(4, createIndexResponse.hits.hits.size());
        assertEquals(createIndexResponse.hits.hits.get(0)._id, "1");
        assertEquals(createIndexResponse.hits.hits.get(1)._id, "2");
        assertEquals(createIndexResponse.hits.hits.get(2)._id, "3");
        assertEquals(createIndexResponse.hits.hits.get(3)._id, "4");
    }

}
