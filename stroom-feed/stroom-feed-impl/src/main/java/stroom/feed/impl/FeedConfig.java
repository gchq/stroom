package stroom.feed.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class FeedConfig implements IsConfig {
    private String unknownClassification = "UNKNOWN CLASSIFICATION";
    private String feedNamePattern = "^[A-Z0-9_-]{3,}$";
    private CacheConfig feedDocCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.SECONDS)
            .build();

    @JsonPropertyDescription("The classification banner to display for data if one is not defined")
    public String getUnknownClassification() {
        return unknownClassification;
    }

    public void setUnknownClassification(final String unknownClassification) {
        this.unknownClassification = unknownClassification;
    }

    @JsonPropertyDescription("The regex pattern for feed names")
    public String getFeedNamePattern() {
        return feedNamePattern;
    }

    public void setFeedNamePattern(final String feedNamePattern) {
        this.feedNamePattern = feedNamePattern;
    }

    public CacheConfig getFeedDocCache() {
        return feedDocCache;
    }

    public void setFeedDocCache(final CacheConfig feedDocCache) {
        this.feedDocCache = feedDocCache;
    }

    @Override
    public String toString() {
        return "FeedConfig{" +
                "unknownClassification='" + unknownClassification + '\'' +
                ", feedNamePattern='" + feedNamePattern + '\'' +
                ", feedDocCache=" + feedDocCache +
                '}';
    }
}
