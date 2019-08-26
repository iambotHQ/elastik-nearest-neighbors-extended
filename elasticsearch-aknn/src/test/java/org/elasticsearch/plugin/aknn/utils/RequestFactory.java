package org.elasticsearch.plugin.aknn.utils;

import org.elasticsearch.plugin.aknn.models.CreateIndexRequest;
import org.elasticsearch.plugin.aknn.models.CreateModelRequest;
import org.elasticsearch.plugin.aknn.models.SimilaritySearchRequest;

import java.util.List;

public class RequestFactory {
    public static String index = "twitter_images";
    public static String indexType = "_doc";
    public static String modelIndex = "aknn_models";
    public static String modelType = "aknn_model";
    public static String modelId = "twitter_image_search";

    public static CreateModelRequest createModelRequest(int nbTables, int nbBits) {
        return new CreateModelRequest(
                modelIndex,
                modelType,
                modelId,
            new CreateModelRequest.Source(
                    "LSH model for Twitter image similarity search",
                    nbTables,
                    nbBits,
                    3
            )
        );
    }

    public static CreateIndexRequest createIndexRequest(List<CreateIndexRequest.Doc> docs) {
        return new CreateIndexRequest(
                index,
                indexType,
                modelIndex + "/" + modelType + "/" + modelId,
                docs
        );
    }

    public static SimilaritySearchRequest similaritySearchRequest(SimilaritySearchRequest.Query query) {
        return new SimilaritySearchRequest(
                index,
                indexType,
                modelIndex + "/" + modelType + "/" + modelId,
                query
        );
    }
}
