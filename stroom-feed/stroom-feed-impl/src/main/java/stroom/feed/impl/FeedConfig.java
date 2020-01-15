package stroom.feed.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.IsConfig;
import stroom.util.shared.ValidRegex;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class FeedConfig extends IsConfig {

    public static final String PROP_NAME_FEED_NAME_PATTERN = "feedNamePattern";
    public static final String PROP_NAME_FEED_DOC_CACHE = "feedDocCache";

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
    @JsonProperty(PROP_NAME_FEED_NAME_PATTERN)
    @ValidRegex
    public String getFeedNamePattern() {
        return feedNamePattern;
    }

    @SuppressWarnings("unused")
    public void setFeedNamePattern(final String feedNamePattern) {
        this.feedNamePattern = feedNamePattern;
    }

    @JsonProperty(PROP_NAME_FEED_DOC_CACHE)
    public CacheConfig getFeedDocCache() {
        return feedDocCache;
    }

    @SuppressWarnings("unused")
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
