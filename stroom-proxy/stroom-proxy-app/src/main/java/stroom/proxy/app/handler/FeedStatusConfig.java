package stroom.proxy.app.handler;

import stroom.proxy.feed.remote.FeedStatus;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_API_KEY = "apiKey";

    @JsonProperty
    @JsonPropertyDescription("Turn feed status checking on/off.")
    private final Boolean enabled;

    @JsonProperty
    @JsonPropertyDescription("How should proxy treat incoming data if feed status checking is turned off or we are" +
                             " unable to fetch the status.")
    private final FeedStatus defaultStatus;

    @JsonProperty("url")
    @JsonPropertyDescription("The remote URL to fetch feed status from if enabled.")
    private final String feedStatusUrl;

    @JsonProperty(PROP_NAME_API_KEY)
    @JsonPropertyDescription("The api key to use to authenticate with the feed status service.")
    private final String apiKey;

    @RequiresProxyRestart
    @NotNull
    @JsonProperty("feedStatusCache")
    @JsonPropertyDescription("Configure caching of the fetched feed status.")
    private final CacheConfig feedStatusCache;

    public FeedStatusConfig() {
        enabled = true;
        defaultStatus = FeedStatus.Receive;
        feedStatusUrl = null;
        apiKey = null;
        feedStatusCache = buildDefaultCacheConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FeedStatusConfig(@JsonProperty("enabled") final Boolean enabled,
                            @JsonProperty("defaultStatus") final FeedStatus defaultStatus,
                            @JsonProperty("url") final String feedStatusUrl,
                            @JsonProperty(PROP_NAME_API_KEY) final String apiKey,
                            @JsonProperty("feedStatusCache") final CacheConfig feedStatusCache) {
        this.enabled = enabled;
        this.defaultStatus = defaultStatus;
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
                .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE)
                .build();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public FeedStatus getDefaultStatus() {
        return defaultStatus;
    }

    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

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
        return Objects.equals(enabled, that.enabled) &&
               defaultStatus == that.defaultStatus &&
               Objects.equals(feedStatusUrl, that.feedStatusUrl) &&
               Objects.equals(apiKey, that.apiKey) &&
               Objects.equals(feedStatusCache, that.feedStatusCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, defaultStatus, feedStatusUrl, apiKey, feedStatusCache);
    }

    @Override
    public String toString() {
        return "FeedStatusConfig{" +
               "enabled=" + enabled +
               ", defaultStatus=" + defaultStatus +
               ", feedStatusUrl='" + feedStatusUrl + '\'' +
               ", apiKey='" + apiKey + '\'' +
               ", feedStatusCache=" + feedStatusCache +
               '}';
    }
}
