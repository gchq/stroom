package stroom.proxy.app.handler;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    private final String feedStatusUrl;
    private final String apiKey;
    private final CacheConfig feedStatusCache;

    public FeedStatusConfig() {
        feedStatusUrl = null;
        apiKey = null;
        feedStatusCache = buildDefaultCacheConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FeedStatusConfig(@JsonProperty("url") final String feedStatusUrl,
                            @JsonProperty("apiKey") final String apiKey,
                            @JsonProperty("feedStatusCache") final CacheConfig feedStatusCache) {
        this.feedStatusUrl = feedStatusUrl;
        this.apiKey = apiKey;

        this.feedStatusCache = feedStatusCache == null
                ? buildDefaultCacheConfig()
                : feedStatusCache;
    }

    private static CacheConfig buildDefaultCacheConfig() {
        return CacheConfig
                .builder()
                .maximumSize(1_000L)
                .build();
    }

    @JsonProperty("url")
    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    @JsonProperty("apiKey")
    public String getApiKey() {
        return apiKey;
    }

    @RequiresProxyRestart
    @NotNull
    @JsonProperty("feedStatusCache")
    public CacheConfig getFeedStatusCache() {
        return feedStatusCache;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeedStatusConfig that = (FeedStatusConfig) o;
        return Objects.equals(feedStatusUrl, that.feedStatusUrl) && Objects.equals(apiKey,
                that.apiKey) && Objects.equals(feedStatusCache, that.feedStatusCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedStatusUrl, apiKey, feedStatusCache);
    }

    @Override
    public String toString() {
        return "FeedStatusConfig{" +
                "feedStatusUrl='" + feedStatusUrl + '\'' +
                ", apiKey='" + apiKey + '\'' +
                ", feedStatusCache=" + feedStatusCache +
                '}';
    }
}
