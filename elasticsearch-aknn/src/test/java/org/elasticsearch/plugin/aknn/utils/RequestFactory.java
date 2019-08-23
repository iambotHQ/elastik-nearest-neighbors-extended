package org.elasticsearch.plugin.aknn.utils;

import org.elasticsearch.plugin.aknn.models.CreateIndexRequest;
import org.elasticsearch.plugin.aknn.models.CreateModelRequest;
import org.elasticsearch.plugin.aknn.models.SimilaritySearchRequest;

import java.util.List;

public class RequestFactory {
    public static CreateModelRequest createModelRequest() {
        return new CreateModelRequest(
            "aknn_models",
            "aknn_model",
            "twitter_image_search",
            new CreateModelRequest.Source(
                    "LSH model for Twitter image similarity search",
                    64,
                    18,
                    3
            )
        );
    }

    public static CreateIndexRequest createIndexRequest(List<CreateIndexRequest.Doc> docs) {
        return new CreateIndexRequest(
                "twitter_images",
                "_doc",
                "aknn_models/aknn_model/twitter_image_search",
                docs
        );
    }

    public static SimilaritySearchRequest similaritySearchRequest(SimilaritySearchRequest.Query query) {
        return new SimilaritySearchRequest(
                "twitter_images",
                "_doc",
                "aknn_models/aknn_model/twitter_image_search",
                query
        );
    }
}
