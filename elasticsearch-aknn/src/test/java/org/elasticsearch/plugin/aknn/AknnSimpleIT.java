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

import org.apache.commons.math3.random.*;
import org.apache.commons.math3.util.Pair;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
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
     * Test that search results returned by _aknn_search_vec are in correct order
     * @throws IOException if performing a request fails
     */
    public void testSearchResultsOrder() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(64, 18, 3));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refresh();

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        for(int i = 0; i < 4; i++) {
            if(similaritySearchResponse.hits.hits.size() > i) {
                assertEquals(String.valueOf(i + 1), similaritySearchResponse.hits.hits.get(i)._id);
            }
        }
    }

    /**
     * Test that indexing a document with the same ID results in update
     * @throws IOException if performing a request fails
     */
    public void testVectorUpdate() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(64, 18, 3));
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
    public void testLSHSimilar() throws IOException {
        final int batchSize = 1000;
        final int numBatches = 10;
        final int nbDimensions = 50;
        final int takeN = 10;
        final int k1 = 500;
        aknnAPI.createModel(RequestFactory.createModelRequest(100, 8, nbDimensions));
        List<CreateIndexRequest.Doc> documents = new ArrayList<>();
        RandomGenerator rg = new RandomDataGenerator().getRandomGenerator();
        rg.setSeed(55626L);
        for(int j = 0; j < numBatches; j++) {
            List<CreateIndexRequest.Doc> docs = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                double[] vector = IntStream.generate(() -> 1).mapToDouble(v -> v).limit(nbDimensions).toArray();
                vector[0] = 0.00001 * (j * batchSize + i);
                if (j * batchSize + i > takeN) {
                    vector = Arrays.stream(vector).map(v -> -v).toArray();
                }
                CreateIndexRequest.Source source = new CreateIndexRequest.Source(vector);
                docs.add(new CreateIndexRequest.Doc(String.valueOf(j * batchSize + i), source));
            }
            aknnAPI.createIndex(RequestFactory.createIndexRequest(docs));
            documents.addAll(docs);
        }
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
