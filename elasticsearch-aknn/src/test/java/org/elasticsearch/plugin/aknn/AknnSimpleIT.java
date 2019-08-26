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

    private void refreshAll() {
        refresh(RequestFactory.index, RequestFactory.modelIndex);
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
        aknnAPI.createModel(RequestFactory.createModelRequest(64, 18));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refreshAll();

        SimilaritySearchResponse similaritySearchResponse = aknnAPI.similaritySearch(RequestFactory.similaritySearchRequest(new SimilaritySearchRequest.Query(
                new double[]{ 1.0, 0.0, 0.0 },
                1000,
                10
        )));
        assertNotNull(similaritySearchResponse.hits);
        assertNotNull(similaritySearchResponse.hits.hits);
        for(int i = 0; i < 4; i++) {
            if(similaritySearchResponse.hits.hits.size() > i) {
                assertEquals(similaritySearchResponse.hits.hits.get(i)._id, String.valueOf(i + 1));
            }
        }
    }

    /**
     * Test that indexing a document with the same ID results in update
     * @throws IOException if performing a request fails
     */
    public void testVectorUpdate() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(64, 18));
        aknnAPI.createIndex(RequestFactory.createIndexRequest(simpleDocs));
        refreshAll();

        GetVectorResponse getVectorResponse = aknnAPI.getVector("twitter_images", "_doc", "1");
        assertEquals(getVectorResponse._id, "1");
        assertNotNull(getVectorResponse._source);
        assertNotNull(getVectorResponse._source._aknn_vector);
        assertEquals(getVectorResponse._source._aknn_vector.length, 3);
        assertEquals(getVectorResponse._source._aknn_vector[0], 1.0, 0.001);
        assertEquals(getVectorResponse._source._aknn_vector[1], 0.0, 0.0);
        assertEquals(getVectorResponse._source._aknn_vector[2], 0.3, 0.001);

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
     * Test that similarity search returns good results on big data
     * @throws IOException if performing a request fails
     */
    public void testLSHBig() throws IOException {
        aknnAPI.createModel(RequestFactory.createModelRequest(100, 8));

        Random rand = new Random(55626L);
        List<CreateIndexRequest.Doc> documents = new ArrayList<>();
        for(int j = 0; j < 5; j++) {
            List<CreateIndexRequest.Doc> docs = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                double alpha = rand.nextDouble() * Math.PI * 2;
                double beta = rand.nextDouble() * Math.PI * 2;
                double x = Math.cos(alpha) * Math.cos(beta);
                double z = Math.sin(alpha) * Math.cos(beta);
                double y = Math.sin(beta);
                CreateIndexRequest.Source source = new CreateIndexRequest.Source(new double[]{x, y, z});
                docs.add(new CreateIndexRequest.Doc(String.valueOf(j * i + i), source));
            }
            aknnAPI.createIndex(RequestFactory.createIndexRequest(docs));
            documents.addAll(docs);
        }
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
            idDistances.add(new Pair<>(doc._id, Math.abs(AknnRestAction.cosineSimilarity(
                    Arrays.stream(doc._source._aknn_vector).boxed().collect(Collectors.toList()),
                    Arrays.stream(searchVec).boxed().collect(Collectors.toList())
            ))));
        }
        idDistances.sort(Comparator.comparing(Pair::getSecond));

        List<String> greedyIds = idDistances.stream().limit(takeN).map(Pair::getFirst).collect(Collectors.toList());

        int numContains = 0;
        for(int i = 0; i < takeN; i++) {
            if (greedyIds.contains(lshIds.get(i))) {
                numContains++;
                System.out.println(idDistances.get(i).getSecond() + " | " + similaritySearchResponse.hits.hits.get(i)._score);
            }
        }

        /*System.out.println("Greedy: " + greedyIds.toString());
        System.out.println("LSH: " + lshIds.toString());
        System.out.println("Num intersect: " + numContains);*/
        assertTrue(numContains >= 6);
    }
}
