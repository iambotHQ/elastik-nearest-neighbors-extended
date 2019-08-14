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

import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class AknnSimpleIT extends ESIntegTestCase {

    private Client client;
    private RestClient restClient;

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

    private Response performJSONRequest(String jsonPath, String endpoint) throws IOException {
        Request postReq = new Request("POST", endpoint);
        postReq.setEntity(new StringEntity(
                getResourceFileAsString(jsonPath),
                ContentType.APPLICATION_JSON));
        return restClient.performRequest(postReq);
    }

    /**
     * Test that the plugin was installed correctly by hitting the _cat/plugins endpoint.
     * @throws IOException if performing request fails
     */
    public void testPluginInstallation() throws IOException {
        Response response = restClient.performRequest(new Request("GET", "_cat/plugins"));
        String body = EntityUtils.toString(response.getEntity());
        logger.info(body);
        assertTrue(body.contains("elasticsearch-aknn"));
    }

    public void testCreatingIndex() throws IOException {
        performJSONRequest("createModel.json", "_aknn_create");
        /*performJSONRequest("createIndex.json", "_aknn_index");
        Response response = performJSONRequest("similaritySearch.json", "_aknn_search_vec");
        String body = EntityUtils.toString(response.getEntity());
        logger.info(body);*/
        //assertTrue(body.contains("elasticsearch-aknn"));
    }

}
