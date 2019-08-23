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
import org.apache.commons.math3.util.Pair;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.plugin.aknn.models.CreateIndexRequest;
import org.elasticsearch.plugin.aknn.models.SimilaritySearchRequest;
import org.elasticsearch.plugin.aknn.models.SimilaritySearchResponse;
import org.elasticsearch.plugin.aknn.models.GetVectorResponse;
import org.elasticsearch.plugin.aknn.utils.AknnAPI;
import org.elasticsearch.plugin.aknn.utils.CosineSimilarity;
import org.elasticsearch.plugin.aknn.utils.RequestFactory;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AknnSimpleIT extends ESIntegTestCase {

    private Client client;
    private RestClient restClient;
    private AknnAPI aknnAPI;
    private List<CreateIndexRequest.Doc> simpleDocs = new ArrayList<>();

    public AknnSimpleIT() {
        simpleDocs.add(new CreateIndexRequest.Doc("1", new CreateIndexRequest.Source(new double[]{ 1.0, 0.0, 0.0 })));
        simpleDocs.add(new CreateIndexRequest.Doc("2", new CreateIndexRequest.Source(new double[]{ 1.0, 1.0, 0.0 })));
        simpleDocs.add(new CreateIndexRequest.Doc("3", new CreateIndexRequest.Source(new double[]{ 0.0, 1.0, 0.0 })));
        simpleDocs.add(new CreateIndexRequest.Doc("4", new CreateIndexRequest.Source(new double[]{ 0.0, 1.0, 1.0 })));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        client = client();
        restClient = getRestClient();
        aknnAPI = new AknnAPI(restClient);
    }

    private void refreshAll() {
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
        aknnAPI.createModel(RequestFactory.createModelRequest());
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refreshAll();

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        assertEquals(4, similaritySearchResponse.hits.hits.size());

        SimilaritySearchResponse similaritySearchResponseIndexed = aknnAPI.similaritySearch(1000, 10, "twitter_images", "doc", "1");
        assertNotNull(similaritySearchResponseIndexed.hits);
        assertNotNull(similaritySearchResponseIndexed.hits.hits);
        assertEquals(4, similaritySearchResponseIndexed.hits.hits.size());

        for(int i = 0; i < similaritySearchResponse.hits.hits.size(); i++) {
            assertEquals(similaritySearchResponse.hits.hits.get(i)._score, similaritySearchResponseIndexed.hits.hits.get(i)._score, 0.001);
        }
    }

    /**
     * Test that search results returned by _aknn_search_vec are in correct order
     * @throws IOException if performing a request fails
     */
    public void testSearchResultsOrder() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest());
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refreshAll();

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        assertEquals(4, similaritySearchResponse.hits.hits.size());
        assertEquals(similaritySearchResponse.hits.hits.get(0)._id, "1");
        assertEquals(similaritySearchResponse.hits.hits.get(1)._id, "2");
        assertEquals(similaritySearchResponse.hits.hits.get(2)._id, "3");
        assertEquals(similaritySearchResponse.hits.hits.get(3)._id, "4");
    }

    /**
     * Test that indexing a document with the same ID results in update
     * @throws IOException if performing a request fails
     */
    public void testVectorUpdate() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest());
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refreshAll();

        GetVectorResponse getVectorResponse = aknnAPI.getVector("twitter_images", "_doc", "1");
        assertEquals(getVectorResponse._id, "1");
        assertNotNull(getVectorResponse._source);
        assertNotNull(getVectorResponse._source._aknn_vector);
        assertEquals(getVectorResponse._source._aknn_vector.length, 3);
        assertEquals(getVectorResponse._source._aknn_vector[0], 1.0, 0.001);
        assertEquals(getVectorResponse._source._aknn_vector[1], 0.0, 0.0);
        assertEquals(getVectorResponse._source._aknn_vector[2], 0.0, 0.0);

        CreateIndexRequest.Doc docIdx = new CreateIndexRequest.Doc("1", new CreateIndexRequest.Source(new double[]{ 0.0, 0.0, 0.0 }));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(Collections.singletonList(docIdx)));
        refreshAll();

        getVectorResponse = aknnAPI.getVector("twitter_images", "_doc", "1");
        assertEquals(getVectorResponse._id, "1");
        assertNotNull(getVectorResponse._source);
        assertNotNull(getVectorResponse._source._aknn_vector);
        assertEquals(getVectorResponse._source._aknn_vector.length, 3);
        assertEquals(getVectorResponse._source._aknn_vector[0], 0.0, 0.0);
        assertEquals(getVectorResponse._source._aknn_vector[1], 0.0, 0.0);
        assertEquals(getVectorResponse._source._aknn_vector[2], 0.0, 0.0);
    }

    /**
     * Test that LSH works for big data
     * @throws IOException if performing a request fails
     */
    public void testLSHBig() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest());

        Random rand = new Random(55626L);
        List<CreateIndexRequest.Doc> documents = new ArrayList<>();
        for(int i = 0; i < 100000; i++) {
            double alpha = rand.nextDouble() * Math.PI * 2;
            double beta = rand.nextDouble() * Math.PI * 2;
            double x = Math.cos(alpha) * Math.cos(beta);
            double z = Math.sin(alpha) * Math.cos(beta);
            double y = Math.sin(beta);
            CreateIndexRequest.Source source = new CreateIndexRequest.Source(new double[]{ x, y, z });
            documents.add(new CreateIndexRequest.Doc(String.valueOf(i), source));
        }
        aknnAPI.createIndex(RequestFactory.createIndexRequest(documents));
        refreshAll();

        double[] searchVec = new double[]{ 1.0, 0.0, 0.0 };
        int takeN = 10;

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                searchVec,
                1000,
                takeN
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        assertEquals(similaritySearchResponse.hits.hits.size(), takeN);
        List<String> lshIds = similaritySearchResponse.hits.hits.stream().map((hit) -> hit._id).collect(Collectors.toList());

        // greedy cosine similarity
        List<Pair<String, Double>> idDistances = new ArrayList<>();
        for(CreateIndexRequest.Doc doc : documents) {
            idDistances.add(new Pair<>(doc._id, Math.abs(CosineSimilarity.calc(doc._source._aknn_vector, searchVec))));
        }
        idDistances.sort(Comparator.comparing(Pair::getSecond));
        List<String> greedyIds = idDistances.stream().limit(takeN).map(Pair::getFirst).collect(Collectors.toList());

        int numContains = 0;
        for(int i = 0; i < takeN; i++) {
            if (greedyIds.contains(lshIds.get(i))) {
                numContains++;
            }
        }

        assertTrue(numContains > 7);
    }
}
