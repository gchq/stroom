package stroom.proxy.app.handler;

import stroom.receive.common.FeedStatusResourceV2;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.ResourcePaths;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_URL = "url";
    //    public static final String PROP_NAME_API_KEY = "apiKey";
    public static final String DEFAULT_URL_PATH = ResourcePaths.buildAuthenticatedApiPath(
            FeedStatusResourceV2.BASE_RESOURCE_PATH,
            FeedStatusResourceV2.GET_FEED_STATUS_PATH_PART);

//    @JsonProperty
//    @JsonPropertyDescription("How should proxy treat incoming data if feed status checking is turned off or we are" +
//                             " unable to fetch the status.")
//    private final FeedStatus defaultStatus;

    @JsonProperty(PROP_NAME_URL)
    @JsonPropertyDescription("The remote URL to fetch feed status from if enabled. If not set the default " +
                             "path will be combined with the downstreamHost.")
    private final String feedStatusUrl;

//    @JsonProperty(PROP_NAME_API_KEY)
//    @JsonPropertyDescription("The api key to use to authenticate with the feed status service.")
//    private final String apiKey;

    @RequiresProxyRestart
    @NotNull
    @JsonProperty("feedStatusCache")
    @JsonPropertyDescription("Configure caching of the fetched feed status.")
    private final CacheConfig feedStatusCache;

    public FeedStatusConfig() {
//        defaultStatus = FeedStatus.Receive;
        feedStatusUrl = null;
//        apiKey = null;
        feedStatusCache = buildDefaultCacheConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FeedStatusConfig(
//            @JsonProperty("defaultStatus") final FeedStatus defaultStatus,
            @JsonProperty(PROP_NAME_URL) final String feedStatusUrl,
//            @JsonProperty(PROP_NAME_API_KEY) final String apiKey,
            @JsonProperty("feedStatusCache") final CacheConfig feedStatusCache) {
//        this.defaultStatus = defaultStatus;
        this.feedStatusUrl = feedStatusUrl;
//        this.apiKey = apiKey;
        this.feedStatusCache = Objects.requireNonNullElseGet(
                feedStatusCache, FeedStatusConfig::buildDefaultCacheConfig);
    }

    private static CacheConfig buildDefaultCacheConfig() {
        return CacheConfig
                .builder()
                .maximumSize(1_000L)
                .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE)
                .build();
    }

//    public FeedStatus getDefaultStatus() {
//        return defaultStatus;
//    }

    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

//    public String getApiKey() {
//        return apiKey;
//    }

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
        return
//                defaultStatus == that.defaultStatus &&
                Objects.equals(feedStatusUrl, that.feedStatusUrl) &&
//                Objects.equals(apiKey, that.apiKey) &&
                Objects.equals(feedStatusCache, that.feedStatusCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
//                defaultStatus,
                feedStatusUrl,
//                apiKey,
                feedStatusCache);
    }

    @Override
    public String toString() {
        return "FeedStatusConfig{" +
//               ", defaultStatus=" + defaultStatus +
               ", feedStatusUrl='" + feedStatusUrl + '\'' +
//               ", apiKey='" + apiKey + '\'' +
               ", feedStatusCache=" + feedStatusCache +
               '}';
    }
}
