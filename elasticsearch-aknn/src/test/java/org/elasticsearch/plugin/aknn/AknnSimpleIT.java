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

import org.apache.commons.math3.util.Pair;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.plugin.aknn.models.CreateIndexRequest;
import org.elasticsearch.plugin.aknn.models.SimilaritySearchRequest;
import org.elasticsearch.plugin.aknn.models.SimilaritySearchResponse;
import org.elasticsearch.plugin.aknn.models.GetVectorResponse;
import org.elasticsearch.plugin.aknn.utils.AknnAPI;
import org.elasticsearch.plugin.aknn.utils.RequestFactory;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AknnSimpleIT extends ESIntegTestCase {

    private RestClient restClient;
    private AknnAPI aknnAPI;
    private List<CreateIndexRequest.Doc> simpleDocs = Arrays.asList(
        new CreateIndexRequest.Doc("1", new CreateIndexRequest.Source(new double[]{ 1.0, 0.0, 0.3 })),
        new CreateIndexRequest.Doc("2", new CreateIndexRequest.Source(new double[]{ 1.0, 1.0, 0.3 })),
        new CreateIndexRequest.Doc("3", new CreateIndexRequest.Source(new double[]{ 0.5, 1.0, 0.3 })),
        new CreateIndexRequest.Doc("4", new CreateIndexRequest.Source(new double[]{ 0.0, 1.0, 0.6 }))
    );

    @Before
    public void setUp() throws Exception {
        super.setUp();
        restClient = getRestClient();
        aknnAPI = new AknnAPI(restClient);
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
     * Test that a model create returns 201 for create
     */
    public void testModelCreate() throws IOException {
        Response response = aknnAPI.createModel(RequestFactory.createModelRequest(200, 1));
        assertEquals(201, response.getStatusLine().getStatusCode());
    }

    /**
     * Test that a model if already exsists, return 200
     * @throws IOException if performing a request fails
     */
    public void testModelImmutable() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(200, 1));
        refresh();
        Response response = aknnAPI.createModel(RequestFactory.createModelRequest(201, 1));
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    /**
     * Test that search results returned by _aknn_search_vec are sorted from most similar to least by default
     * @throws IOException if performing a request fails
     */
    public void testSearchResultsSimilarityOrder() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(200, 1));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refresh();

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        for(int i = 0; i < similaritySearchResponse.hits.hits.size(); i++) {
            assertEquals(String.valueOf(i + 1), similaritySearchResponse.hits.hits.get(i)._id);
        }
    }

    /**
     * Test that search results returned by _aknn_search_vec contain extra data
     * @throws IOException if performing a request fails
     */
    public void testExtraData() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(16, 8));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(Arrays.asList(
                new CreateIndexRequest.Doc("1", new CreateIndexRequest.Source(new double[]{ 1.0, 0.0, 0.0 }, "extras!"))
        )));
        refresh();

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        assertEquals(1, similaritySearchResponse.hits.hits.size());
        assertNotNull(similaritySearchResponse.hits.hits.get(0));
        assertNotNull(similaritySearchResponse.hits.hits.get(0)._source);
        assertEquals("extras!", similaritySearchResponse.hits.hits.get(0)._source.extraData);
    }

    /**
     * Test that param order works
     * @throws IOException if performing a request fails
     */
    public void testSearchResultsOrder() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(200, 1));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refresh();

        SimilaritySearchResponse desc = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )), true);
        assertNotNull(desc.hits);
        assertNotNull(desc.hits.hits);

        SimilaritySearchResponse asc = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )), false);
        assertNotNull(asc.hits);
        assertNotNull(asc.hits.hits);
        assertEquals(desc.hits.hits.size(), asc.hits.hits.size());
        for(int i = 0; i < desc.hits.hits.size(); i++) {
            assertEquals(String.valueOf(i + 1), desc.hits.hits.get(i)._id);
        }
        for(int i = 0; i < asc.hits.hits.size(); i++) {
            assertEquals(String.valueOf(asc.hits.hits.size() - i), asc.hits.hits.get(i)._id);
        }
    }

    /**
     * Test that indexing a document with the same ID results in update
     * @throws IOException if performing a request fails
     */
    public void testVectorUpdate() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(64, 18));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refresh();

        GetVectorResponse getVectorResponse = aknnAPI.getVector("twitter_images", "_doc", "1");
        assertEquals(getVectorResponse._id, "1");
        assertNotNull(getVectorResponse._source);
        assertNotNull(getVectorResponse._source._aknn_vector);
        assertEquals(3, getVectorResponse._source._aknn_vector.length);
        assertEquals(getVectorResponse._source._aknn_vector[0], 1.0, 0.001);
        assertEquals(getVectorResponse._source._aknn_vector[1], 0.0, 0.0);
        assertEquals(getVectorResponse._source._aknn_vector[2], 0.3, 0.001);

        CreateIndexRequest.Doc docIdx = new CreateIndexRequest.Doc("1", new CreateIndexRequest.Source(new double[]{ 0.0, 0.0, 0.0 }));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(Collections.singletonList(docIdx)));
        refresh();

        getVectorResponse = aknnAPI.getVector("twitter_images", "_doc", "1");
        assertEquals(getVectorResponse._id, "1");
        assertNotNull(getVectorResponse._source);
        assertNotNull(getVectorResponse._source._aknn_vector);
        assertEquals(3, getVectorResponse._source._aknn_vector.length);
        assertEquals(0.0, getVectorResponse._source._aknn_vector[0], 0.0);
        assertEquals(0.0, getVectorResponse._source._aknn_vector[1], 0.0);
        assertEquals(0.0, getVectorResponse._source._aknn_vector[2], 0.0);
    }

    /**
     * Test that similarity search returns similar results
     * @throws IOException if performing a request fails
     */
    public void testLSHSimilar() throws IOException, InterruptedException {
        final int numDocs = 1000;
        final int nbDimensions = 50;
        final int takeN = 10;
        final int k1 = 500;
        aknnAPI.createModel(RequestFactory.createModelRequest(100, 8));
        admin().indices().prepareCreate(RequestFactory.index)
                .addMapping(RequestFactory.indexType, "_aknn_vector", "index=false,type=double")
                .get();
        ExecutorService workerPool = Executors.newFixedThreadPool(16);
        List<CreateIndexRequest.Doc> documents = new ArrayList<>();
        for(int i = 0; i < numDocs; i++) {
            double[] vector = IntStream.generate(() -> 1).mapToDouble(v -> v).limit(nbDimensions).toArray();
            vector[0] = 0.00001 * i;
            if (i > takeN) {
                vector = Arrays.stream(vector).map(v -> -v).toArray();
            }
            CreateIndexRequest.Source source = new CreateIndexRequest.Source(vector);
            CreateIndexRequest.Doc doc = new CreateIndexRequest.Doc(String.valueOf(i), source);
            documents.add(doc);
            final int ij = i;
            workerPool.submit(() -> {
                aknnAPI.createIndex(RequestFactory.createIndexRequest(Collections.singletonList(doc)));
                return null;
            });
        }
        workerPool.shutdown();
        workerPool.awaitTermination(24L, TimeUnit.HOURS);
        refresh();

        double[] searchVec = IntStream.generate(() -> 1).mapToDouble(v -> v).limit(nbDimensions).toArray();
        searchVec[0] = 0.0;

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                searchVec,
                k1,
                takeN
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        assertEquals(takeN, similaritySearchResponse.hits.hits.size());
        List<String> lshIds = similaritySearchResponse.hits.hits.stream().map((hit) -> hit._id).collect(Collectors.toList());

        // greedy cosine similarity
        List<Pair<String, Double>> idDistances = new ArrayList<>();
        for(CreateIndexRequest.Doc doc : documents) {
            idDistances.add(new Pair<>(doc._id, AknnRestAction.cosineSimilarity(
                    Arrays.stream(doc._source._aknn_vector).boxed().collect(Collectors.toList()),
                    Arrays.stream(searchVec).boxed().collect(Collectors.toList())
            )));
        }
        idDistances.sort(Comparator.comparing(Pair::getSecond, Comparator.reverseOrder()));

        List<String> greedyIds = idDistances.stream().limit(takeN).map(Pair::getFirst).collect(Collectors.toList());

        int numContains = 0;
        for(int i = 0; i < takeN; i++) {
            if (greedyIds.contains(lshIds.get(i))) {
                numContains++;
            }
        }

        /*System.out.println("Greedy: " + idDistances.stream().limit(takeN).collect(Collectors.toList()));
        System.out.println("LSH: " + similaritySearchResponse.hits.hits.stream().map(v -> new Pair<>(v._id, v._score)).collect(Collectors.toList()));
        System.out.println("Num intersect: " + numContains);*/
        assertTrue(numContains >= 9);
    }
}
