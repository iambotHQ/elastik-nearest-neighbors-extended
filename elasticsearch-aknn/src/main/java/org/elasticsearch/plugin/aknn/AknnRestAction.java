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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.Level;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Randomness;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Random;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class AknnRestAction extends BaseRestHandler {

    public static String NAME = "_aknn";
    private final String NAME_SEARCH = "_aknn_search";
    private final String NAME_SEARCH_VEC = "_aknn_search_vec";
    private final String NAME_INDEX = "_aknn_index";
    private final String NAME_CREATE = "_aknn_create";
    private final String NAME_CREATE_RANDOM = "_aknn_create_random";
    private final String NAME_CLEAR_CACHE = "_aknn_clear_cache";

    private final String RESCORE_COSINE = "COSINE";
    private final String RESCORE_NONE = "NONE";

    // TODO: check how parameters should be defined at the plugin level.
    private final String HASHES_KEY = "_aknn_hashes";
    private final String VECTOR_KEY = "_aknn_vector";
    private final Integer K1_DEFAULT = 99;
    private final Integer K2_DEFAULT = 10;
    private final String RESCORE_DEFAULT = RESCORE_COSINE;
    private final Integer MINIMUM_DEFAULT = 1;

    // TODO: add an option to the index endpoint handler that empties the cache.
    private Cache<Object, Object> lshModelCache;
    private ExecutorService indexingExecutorService;
    private ExecutorService queryingExecutorService;

    @Inject
    public AknnRestAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(GET, "/{index}/{type}/{id}/" + NAME_SEARCH, this);
        controller.registerHandler(POST, NAME_SEARCH_VEC, this);
        controller.registerHandler(POST, NAME_INDEX, this);
        controller.registerHandler(POST, NAME_CREATE, this);
        controller.registerHandler(POST, NAME_CREATE_RANDOM, this);
        controller.registerHandler(GET, NAME_CLEAR_CACHE, this);

        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            Config cfg = ConfigFactory.load(AknnRestAction.class.getClassLoader());
            indexingExecutorService = createExecutorService(cfg, "index-executor-service");
            queryingExecutorService = createExecutorService(cfg, "query-executor-service");
            lshModelCache = CacheBuilder.builder()
                    .setMaximumWeight(cfg.getLong("lsh-cache.maxSizeMb") * 1000000L)
                    .weigher((s, lshModel) -> ((LshModel) lshModel).estimateBytesUsage())
                    .build();
            return null;
        });
    }

    private ThreadPoolExecutor createExecutorService(Config cfg, String executor) {
        return new ThreadPoolExecutor(
                cfg.getInt(executor+".corePoolSize"),
                cfg.getInt(executor+".maximumPoolSize"),
                cfg.getLong(executor+".keepAliveTimeMs"),
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );
    }

    // @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        // wrap whole task in another Thread
        RunnableWithResult<RestChannelConsumer> task = new RunnableWithResult<>(() -> {
            if (restRequest.path().endsWith(NAME_SEARCH_VEC))
                return handleSearchVecRequest(restRequest, client);
            else if (restRequest.path().endsWith(NAME_SEARCH))
                return handleSearchRequest(restRequest, client);
            else if (restRequest.path().endsWith(NAME_INDEX))
                return handleIndexRequest(restRequest, client);
            else if (restRequest.path().endsWith(NAME_CLEAR_CACHE))
                return handleClearRequest(restRequest, client);
            else if (restRequest.path().endsWith(NAME_CREATE))
                return handleCreateRequest(restRequest, client, false);
            else
                return handleCreateRequest(restRequest, client, true);
        });

        ExecutorService executorService = null;

            if (restRequest.path().endsWith(NAME_SEARCH_VEC))
                executorService = queryingExecutorService;
            else if (restRequest.path().endsWith(NAME_SEARCH))
                executorService = queryingExecutorService;
            else if (restRequest.path().endsWith(NAME_INDEX))
                executorService = queryingExecutorService;
            else if (restRequest.path().endsWith(NAME_CLEAR_CACHE))
                executorService = queryingExecutorService;
            else
                executorService = indexingExecutorService;

        executorService.submit(task);
        try {
            return task.getResult();
        } catch (ElasticsearchException e) {
            logger.log(Level.ERROR, "Unexpected Elasticsearch exception", e);
            throw e;
        } catch (Exception e) {
            logger.log(Level.ERROR, "Unexpected exception", e);
            throw new AknnException(e);
        }
    }

    public static Double cosineSimilarity(List<Double> first, List<Double> second) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < first.size(); i++) {
            double a = first.get(i), b = second.get(i);
            dotProduct += a * b;
            normA += Math.pow(a, 2.0);
            normB += Math.pow(b, 2.0);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Loading LSH model refactored as function
    //TODO Fix issues with stopwatch 
    public LshModel initLsh(String aknnURI, NodeClient client) throws ExecutionException {
        StopWatch stopWatch = new StopWatch("StopWatch to load LSH cache");
        LshModel model = (LshModel) lshModelCache.computeIfAbsent(aknnURI, key -> {
            // Get the Aknn document.
            logger.debug("Get Aknn model document from {}", aknnURI);
            stopWatch.start("Get Aknn model document");
            String[] annURITokens = aknnURI.split("/");
            GetResponse aknnGetResponse = client.prepareGet(annURITokens[0], annURITokens[1], annURITokens[2]).get();
            stopWatch.stop();

            // Instantiate LSH from the source map.
            logger.debug("Parse Aknn model document");
            stopWatch.start("Parse Aknn model document");
            LshModel lshModel = LshModel.fromMap(aknnGetResponse.getSourceAsMap());
            stopWatch.stop();
            return lshModel;
        });
        return model;
    }

    //  Query execution refactored as function and added wrapper query
    private List<Map<String, Object>> queryLsh(List<Double> queryVector, Map<String, Long> queryHashes, String index,
                                               String type, Integer k1, String rescore, String filterString, Integer minimumShouldMatch,
                                               Boolean debug, NodeClient client, Boolean orderDesc) {
        // Retrieve the documents with most matching hashes. https://stackoverflow.com/questions/10773581
        StopWatch stopWatch = new StopWatch("StopWatch to query LSH cache");
        logger.debug("Build boolean query from hashes");
        stopWatch.start("Build boolean query from hashes");
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        for (Map.Entry<String, Long> entry : queryHashes.entrySet()) {
            String termKey = HASHES_KEY + "." + entry.getKey();
            queryBuilder.should(QueryBuilders.termQuery(termKey, entry.getValue()));
        }
        queryBuilder.minimumShouldMatch(minimumShouldMatch);

        if (filterString != null) {
            queryBuilder.filter(new WrapperQueryBuilder(filterString));
        }
        //logger.debug(queryBuilder.toString());
        stopWatch.stop();

        String hashes;
        if (!debug) {
            hashes = HASHES_KEY;
        } else {
            hashes = null;
        }


        logger.debug("Execute boolean search");
        stopWatch.start("Execute boolean search");
        SearchResponse approximateSearchResponse = client
                .prepareSearch(index)
                .setTypes(type)
                .setFetchSource("*", hashes)
                .setQuery(queryBuilder)
                .setSize(k1)
                .get();
        stopWatch.stop();

        // Compute exact KNN on the approximate neighbors.
        // Recreate the SearchHit structure, but remove the vector and hashes.
        logger.debug("Compute exact distance and construct search hits");
        stopWatch.start("Compute exact distance and construct search hits");
        List<Map<String, Object>> modifiedSortedHits = new ArrayList<>();
        for (SearchHit hit : approximateSearchResponse.getHits()) {
            Map<String, Object> hitSource = hit.getSourceAsMap();
            List<Double> hitVector = parseVectorFrom(hitSource);
            if (!debug) {
                hitSource.remove(VECTOR_KEY);
                hitSource.remove(HASHES_KEY);
            }

            Double computedScore;
            if (rescore.equals(RESCORE_COSINE)) {
                computedScore = cosineSimilarity(queryVector, hitVector);
            } else {
                computedScore = (double) hit.getScore();
            }

            modifiedSortedHits.add(new HashMap<String, Object>() {{
                put("_index", hit.getIndex());
                put("_id", hit.getId());
                put("_type", hit.getType());
                put("_score", computedScore);
                put("_source", hitSource);
            }});
        }
        stopWatch.stop();

        if (!rescore.equals(RESCORE_NONE)) {
            logger.debug("Sort search hits by exact distance");
            stopWatch.start("Sort search hits by exact distance");
            Comparator<Double> order = orderDesc ? Comparator.reverseOrder() : Comparator.naturalOrder();
            modifiedSortedHits.sort(Comparator.comparing(x -> (Double) x.get("_score"), order));
            stopWatch.stop();
        } else {
            logger.debug("Exact distance rescoring passed");
        }
        logger.debug("Timing summary for querying\n {}", stopWatch.prettyPrint());
        return modifiedSortedHits;
    }


    private RestChannelConsumer handleSearchRequest(RestRequest restRequest, NodeClient client) throws IOException {
        /**
         * Original handleSearchRequest() refactored for further reusability
         * and added some additional parameters, such as filter query.
         *
         * @param  index    Index name
         * @param  type     Doc type (keep in mind forthcoming _type removal in ES7)
         * @param  id       Query document id
         * @param  filter   String in format of ES bool query filter (excluding
         *                  parent 'filter' node)
         * @param  k1       Number of candidates for scoring
         * @param  k2       Number of hits returned
         * @param  minimum_should_match    number of hashes should match for hit to be returned
         * @param  rescore  If set to 'True' will return results without exact matching stage
         * @param  debug    If set to 'True' will include original vectors and hashes in hits
         * @param  order    One of 'asc' or 'desc' (default)
         * @return Return search hits
         */

        StopWatch stopWatch = new StopWatch("StopWatch to Time Search Request");

        // Parse request parameters.
        stopWatch.start("Parse request parameters");
        final String index = restRequest.param("index");
        final String type = restRequest.param("type");
        final String id = restRequest.param("id");
        final String filter = restRequest.param("filter", null);
        final Integer k1 = restRequest.paramAsInt("k1", K1_DEFAULT);
        final Integer k2 = restRequest.paramAsInt("k2", K2_DEFAULT);
        final Integer minimumShouldMatch = restRequest.paramAsInt("minimum_should_match", MINIMUM_DEFAULT);
        final String rescore = restRequest.param("rescore", RESCORE_DEFAULT);
        final Boolean debug = restRequest.paramAsBoolean("debug", false);
        final Boolean orderDesc = restRequest.param("order", "desc").toUpperCase(Locale.ENGLISH).equals("DESC");
        stopWatch.stop();

        logger.debug("Get query document at {}/{}/{}", index, type, id);
        stopWatch.start("Get query document");
        GetResponse queryGetResponse = client.prepareGet(index, type, id).get();
        Map<String, Object> baseSource = queryGetResponse.getSource();
        stopWatch.stop();

        logger.debug("Parse query document hashes");
        stopWatch.start("Parse query document hashes");
        @SuppressWarnings("unchecked")
        Map<String, Long> queryHashes = (Map<String, Long>) baseSource.get(HASHES_KEY);
        stopWatch.stop();

        stopWatch.start("Parse query document vector");
        List<Double> queryVector = parseVectorFrom(baseSource);
        stopWatch.stop();

        stopWatch.start("Query nearest neighbors");
        List<Map<String, Object>> modifiedSortedHits = queryLsh(queryVector, queryHashes, index, type, k1,
                rescore, filter, minimumShouldMatch, debug, client, orderDesc);

        stopWatch.stop();

        logger.debug("Timing summary\n {}", stopWatch.prettyPrint());

        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("took", stopWatch.totalTime().getMillis());
            builder.field("timed_out", false);
            builder.startObject("hits");
            builder.field("max_score", 0);

            // In some cases there will not be enough approximate matches to return *k2* hits. For example, this could
            // be the case if the number of bits per table in the LSH model is too high, over-partioning the space.
            builder.field("total", min(k2, modifiedSortedHits.size()));
            builder.field("hits", modifiedSortedHits.subList(0, min(k2, modifiedSortedHits.size())));
            builder.endObject();
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer handleSearchVecRequest(RestRequest restRequest, NodeClient client) throws Exception {

        /**
         * Hybrid of refactored handleSearchRequest() and handleIndexRequest()
         * Takes document containing query vector, hashes it, and executing query
         * without indexing.
         *
         * @param  index        Index name
         * @param  type         Doc type (keep in mind forthcoming _type removal in ES7)
         * @param  _aknn_vector Query vector
         * @param  filter       String in format of ES bool query filter (excluding
         *                      parent 'filter' node)
         * @param  k1           Number of candidates for scoring
         * @param  k2           Number of hits returned
         * @param  minimum_should_match    number of hashes should match for hit to be returned
         * @param  rescore      If set to 'True' will return results without exact matching stage
         * @param  debug        If set to 'True' will include original vectors and hashes in hits
         * @param  order        One of 'asc' or 'desc' (default)
         * @return Return search hits
         */


        StopWatch stopWatch = new StopWatch("StopWatch to Time Search Request");

        // Parse request parameters.
        stopWatch.start("Parse request parameters");
        XContentParser xContentParser = XContentHelper.createParser(
                restRequest.getXContentRegistry(),
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                restRequest.content(),
                restRequest.getXContentType());
        @SuppressWarnings("unchecked")
        Map<String, Object> contentMap = xContentParser.mapOrdered();
        @SuppressWarnings("unchecked")
        Map<String, Object> aknnQueryMap = (Map<String, Object>) contentMap.get("query_aknn");
        @SuppressWarnings("unchecked")
        Map<String, ?> filterMap = (Map<String, ?>) contentMap.get("filter");
        String filter = null;
        if (filterMap != null) {
            XContentBuilder filterBuilder = XContentFactory.jsonBuilder()
                    .map(filterMap);
            filter = Strings.toString(filterBuilder);
        }

        final String index = (String) contentMap.get("_index");
        final String type = (String) contentMap.get("_type");
        final String aknnURI = (String) contentMap.get("_aknn_uri");
        final Integer k1 = (Integer) aknnQueryMap.get("k1");
        final Integer k2 = (Integer) aknnQueryMap.get("k2");
        final Integer minimumShouldMatch = restRequest.paramAsInt("minimum_should_match", MINIMUM_DEFAULT);
        final String rescore = restRequest.param("rescore", RESCORE_DEFAULT);
        final Boolean debug = restRequest.paramAsBoolean("debug", false);
        final Boolean orderDesc = restRequest.param("order", "desc").toUpperCase(Locale.ENGLISH).equals("DESC");

        List<Double> queryVector = parseVectorFrom(aknnQueryMap);
        stopWatch.stop();
        // Check if the LshModel has been cached. If not, retrieve the Aknn document and use it to populate the model.
        LshModel lshModel = initLsh(aknnURI, client);

        stopWatch.start("Query nearest neighbors");
        List<Map<String, Object>> modifiedSortedHits;
        if (!lshModel.hasBases()) {
            modifiedSortedHits = new ArrayList<>();
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Long> queryHashes = lshModel.getVectorHashes(queryVector);
            //logger.debug("HASHES: {}", queryHashes);
            modifiedSortedHits = queryLsh(queryVector, queryHashes, index, type, k1, rescore,
                    filter, minimumShouldMatch, debug, client, orderDesc);
        }

        stopWatch.stop();
        logger.debug("Timing summary\n {}", stopWatch.prettyPrint());
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("took", stopWatch.totalTime().getMillis());
            builder.field("timed_out", false);
            builder.startObject("hits");
            builder.field("max_score", 0);

            // In some cases there will not be enough approximate matches to return *k2* hits. For example, this could
            // be the case if the number of bits per table in the LSH model is too high, over-partioning the space.
            builder.field("total", min(k2, modifiedSortedHits.size()));
            builder.field("hits", modifiedSortedHits.subList(0, min(k2, modifiedSortedHits.size())));
            builder.endObject();
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }


    private RestChannelConsumer handleCreateRequest(RestRequest restRequest, NodeClient client, boolean randomBase) throws IOException {

        StopWatch stopWatch = new StopWatch("StopWatch to time create request");
        logger.debug("Parse request");
        stopWatch.start("Parse request");

        XContentParser xContentParser = XContentHelper.createParser(
                restRequest.getXContentRegistry(),
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                restRequest.content(),
                restRequest.getXContentType());
        Map<String, Object> contentMap = xContentParser.mapOrdered();
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceMap = (Map<String, Object>) contentMap.get("_source");


        final String _index = (String) contentMap.get("_index");
        final String _type = (String) contentMap.get("_type");
        final String _id = (String) contentMap.get("_id");
        final String description = (String) sourceMap.get("_aknn_description");
        final Integer nbTables = (Integer) sourceMap.get("_aknn_nb_tables");
        final Integer nbBitsPerTable = (Integer) sourceMap.get("_aknn_nb_bits_per_table");
        final Integer nbDimensions = (Integer) sourceMap.get("_aknn_nb_dimensions");
        stopWatch.stop();


        logger.debug("Create LSH index");
        stopWatch.start("Create LSH index");

        try {
            client.admin().indices()
                    .prepareCreate(_index)
                    .addMapping(_type, "_aknn_bases", "index=false,type=double", "_aknn_bases_seed", "index=false,type=long")
                    .get();
        } catch (ResourceAlreadyExistsException ignored) {
            logger.warn("Index " + _index + " already exists, skipping adding mapping");
            stopWatch.stop();
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("took", stopWatch.totalTime().getMillis());
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        }
        stopWatch.stop();

        logger.debug("Fit LSH model with base vectors");
        stopWatch.start("Fit LSH model with base vectors");
        LshModel lshModel;
        if (randomBase) {
            Random rng = Randomness.get();
            lshModel = new LshModel(nbTables, nbBitsPerTable, nbDimensions, description, rng.nextLong());
        } else {
            @SuppressWarnings("unchecked") final List<List<Double>> vectorSample =
                    (List<List<Double>>) contentMap.get("_aknn_vector_sample");
            lshModel = new LshModel(nbTables, nbBitsPerTable, nbDimensions, description, vectorSample);
        }
        stopWatch.stop();

        logger.debug("Serialize LSH model");
        stopWatch.start("Serialize LSH model");
        Map<String, Object> lshSerialized = lshModel.toMap();
        stopWatch.stop();


        logger.debug("Index LSH model");
        stopWatch.start("Index LSH model");
        client.prepareIndex(_index, _type, _id)
                .setSource(lshSerialized)
                .setCreate(true)
                .get();
        stopWatch.stop();

        logger.debug("Timing summary\n {}", stopWatch.prettyPrint());

        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("took", stopWatch.totalTime().getMillis());
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.CREATED, builder));
        };
    }

    private RestChannelConsumer handleIndexRequest(RestRequest restRequest, NodeClient client) throws Exception {

        StopWatch stopWatch = new StopWatch("StopWatch to time bulk indexing request");

        logger.debug("Parse request parameters");
        stopWatch.start("Parse request parameters");
        XContentParser xContentParser = XContentHelper.createParser(
                restRequest.getXContentRegistry(),
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                restRequest.content(),
                restRequest.getXContentType());
        Map<String, Object> contentMap = xContentParser.mapOrdered();
        final String index = (String) contentMap.get("_index");
        final String type = (String) contentMap.get("_type");
        final String aknnURI = (String) contentMap.get("_aknn_uri");
        final int retryOnConflict = restRequest.paramAsInt("retryOnConflict", 5);
        @SuppressWarnings("unchecked") final List<Map<String, Object>> docs = (List<Map<String, Object>>) contentMap.get("_aknn_docs");
        logger.debug("Received {} docs for indexing", docs.size());
        stopWatch.stop();

        // TODO: check if the index exists. If not, create a mapping which does not index continuous values.
        // This is rather low priority, as I tried it via Python and it doesn't make much difference.

        // Check if the LshModel has been cached. If not, retrieve the Aknn document and use it to populate the model.
        LshModel lshModel = initLsh(aknnURI, client);
        // lazily generate bases if needed
        if (!lshModel.hasBases() && docs.size() > 0) {
            logger.debug("Lazily generate bases");
            stopWatch.start("Lazily generate bases");
            Map<String, Object> doc = docs.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) doc.get("_source");
            List<Double> vector = parseVectorFrom(source);
            lshModel.generateBases(vector.size());
            Map<String, Object> lshSerialized = lshModel.toMap();
            String[] annURITokens = aknnURI.split("/");
            client.prepareIndex(annURITokens[0], annURITokens[1], annURITokens[2])
                    .setSource(lshSerialized)
                    .get();
            stopWatch.stop();
        }

        // Prepare documents for batch indexing.
        logger.debug("Hash documents for indexing");
        stopWatch.start("Hash documents for indexing");
        BulkRequestBuilder bulkIndexRequest = client.prepareBulk();
        for (Map<String, Object> doc : docs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) doc.get("_source");
            List<Double> vector = parseVectorFrom(source);
            source.put(HASHES_KEY, lshModel.getVectorHashes(vector));
            bulkIndexRequest.add(client
                    .prepareUpdate(index, type, String.valueOf(doc.get("_id")))
                    .setDoc(source)
                    .setRetryOnConflict(retryOnConflict)
                    .setDocAsUpsert(true));
        }
        stopWatch.stop();

        logger.debug("Execute bulk indexing");
        stopWatch.start("Execute bulk indexing");
        BulkResponse bulkIndexResponse = bulkIndexRequest.get();
        stopWatch.stop();

        logger.debug("Timing summary\n {}", stopWatch.prettyPrint());

        if (bulkIndexResponse.hasFailures()) {
            logger.error("Indexing failed with message: {}", bulkIndexResponse.buildFailureMessage());
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("took", stopWatch.totalTime().getMillis());
                builder.field("error", bulkIndexResponse.buildFailureMessage());
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, builder));
            };
        }

        logger.debug("Indexed {} docs successfully", docs.size());
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("size", docs.size());
            builder.field("took", stopWatch.totalTime().getMillis());
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer handleClearRequest(RestRequest restRequest, NodeClient client) {
        StopWatch stopWatch = new StopWatch("StopWatch to time clear cache");
        logger.debug("Clearing LSH models cache");
        stopWatch.start("Clearing cache");
        lshModelCache.invalidateAll();
        stopWatch.stop();
        logger.debug("Timing summary\n {}", stopWatch.prettyPrint());

        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("took", stopWatch.totalTime().getMillis());
            builder.field("acknowledged", true);
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private List<Double> parseVectorFrom(Map<String, Object> source) {
        @SuppressWarnings("unchecked")
        List<Object> vec = (List<Object>) source.get(VECTOR_KEY);
        return vec.stream().map(v -> Double.valueOf(v.toString())).collect(Collectors.toList());
    }
}
