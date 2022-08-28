package stroom.proxy.app.handler;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    private final String feedStatusUrl;
    private final String apiKey;
    private final CacheConfig feedStatusCache;

    public FeedStatusConfig() {
        feedStatusUrl = null;
        apiKey = null;
        feedStatusCache = CacheConfig
                .builder()
                .maximumSize(1000L)
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FeedStatusConfig(@JsonProperty("url") final String feedStatusUrl,
                            @JsonProperty("apiKey") final String apiKey,
                            @JsonProperty("feedStatusCache") final CacheConfig feedStatusCache) {
        this.feedStatusUrl = feedStatusUrl;
        this.apiKey = apiKey;

        if (feedStatusCache == null) {
            this.feedStatusCache = CacheConfig
                    .builder()
                    .maximumSize(1000L)
                    .build();
        } else {
            this.feedStatusCache = feedStatusCache;
        }
    }

    @JsonProperty("url")
    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    @JsonProperty("apiKey")
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty("feedStatusCache")
    public CacheConfig getFeedStatusCache() {
        return feedStatusCache;
    }
}
