package stroom.proxy.app.handler;

import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.receive.common.ReceiveDataServlet;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.io.PathCreator;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public final class ForwardHttpPostConfig
        extends AbstractConfig
        implements IsProxyConfig, ForwarderConfig {

    public static final boolean DEFAULT_ADD_OPEN_ID_ACCESS_TOKEN = false;
    public static final boolean DEFAULT_IS_ENABLED = true;
    public static final boolean DEFAULT_LIVENESS_CHECK_ENABLED = true;
    public static final boolean DEFAULT_IS_INSTANT = false;

    public static final String DEFAULT_FORWARD_PATH = ReceiveDataServlet.DATA_FEED_PATH_PART;
    public static final String DEFAULT_LIVENESS_CHECK_PATH = ProxyStatusServlet.PATH_PART;

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String forwardUrl;
    private final String livenessCheckUrl;
    private final boolean livenessCheckEnabled;
    private final String apiKey;
    private final boolean addOpenIdAccessToken;
    private final HttpClientConfiguration httpClient;
    private final ForwardHttpQueueConfig forwardQueueConfig;

    public ForwardHttpPostConfig() {
        enabled = DEFAULT_IS_ENABLED;
        instant = DEFAULT_IS_INSTANT;
        name = null;
        forwardUrl = null;
        livenessCheckUrl = null;
        livenessCheckEnabled = DEFAULT_LIVENESS_CHECK_ENABLED;
        apiKey = null;
        addOpenIdAccessToken = DEFAULT_ADD_OPEN_ID_ACCESS_TOKEN;
        httpClient = createDefaultHttpClientConfiguration();
        forwardQueueConfig = new ForwardHttpQueueConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpPostConfig(@JsonProperty("enabled") final Boolean enabled,
                                 @JsonProperty("instant") final Boolean instant,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("forwardUrl") final String forwardUrl,
                                 @JsonProperty("livenessCheckUrl") final String livenessCheckUrl,
                                 @JsonProperty("livenessCheckEnabled") final Boolean livenessCheckEnabled,
                                 @JsonProperty("apiKey") final String apiKey,
                                 @JsonProperty("addOpenIdAccessToken") final Boolean addOpenIdAccessToken,
                                 @JsonProperty("httpClient") final HttpClientConfiguration httpClient,
                                 @JsonProperty("queue") final ForwardHttpQueueConfig forwardQueueConfig) {
        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_IS_ENABLED);
        this.instant = Objects.requireNonNullElse(instant, DEFAULT_IS_INSTANT);
        this.name = name;
        this.forwardUrl = forwardUrl;
        this.livenessCheckUrl = livenessCheckUrl;
        this.livenessCheckEnabled = Objects.requireNonNullElse(livenessCheckEnabled, DEFAULT_LIVENESS_CHECK_ENABLED);
        this.apiKey = apiKey;
        this.addOpenIdAccessToken = Objects.requireNonNullElse(addOpenIdAccessToken, DEFAULT_ADD_OPEN_ID_ACCESS_TOKEN);
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::createDefaultHttpClientConfiguration);
        this.forwardQueueConfig = Objects.requireNonNullElseGet(forwardQueueConfig, ForwardHttpQueueConfig::new);
    }

    private HttpClientConfiguration createDefaultHttpClientConfiguration() {
        return HttpClientConfiguration
                .builder()
                .timeout(StroomDuration.ofMinutes(1))
                .connectionTimeout(StroomDuration.ofMinutes(1))
                .connectionRequestTimeout(StroomDuration.ofMinutes(1))
                .timeToLive(StroomDuration.ofHours(1))
                .build();
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @Override
    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Should data be forwarded instantly during the receipt process, i.e. must we" +
                             " successfully forward before returning a success response to the sender.")
    public boolean isInstant() {
        return instant;
    }

    @Override
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The unique name of the destination (across all file/http forward destinations. " +
                             "The name is used in the directories on the file system, so do not change the name " +
                             "once proxy has processed data. Must be provided.")
    public String getName() {
        return name;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription(
            "The URL/path to forward on to. " +
            "If this property is not set, the downstreamHost configuration will be combined with the default API " +
            "path (/datafeed). " +
            "If this property is just a path, it will be combined with the downstreamHost configuration. " +
            "Only set this property if you wish to use a non-default path. " +
            "This is pass-through mode if instant is set. " +
            "This property must be set and does NOT fallback to downstreamHost " +
            "or you want to use a different host/port/scheme to that defined in downstreamHost.")
    public String getForwardUrl() {
        return forwardUrl;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "The URL/path to check for liveness of the forward destination. The URL should return a 200 response " +
            "to a GET request for the destination to be considered live. " +
            "If the response from the liveness check is not a 200, forwarding " +
            "will be paused at least until the next liveness check is performed. " +
            "If this property is not set, the downstreamHost configuration will be combined with the default API " +
            "path (/status). " +
            "If this property is just a path, it will be combined with the downstreamHost configuration. " +
            "Only set this property if you wish to use a non-default path. " +
            "or you want to use a different host/port/scheme to that defined in downstreamHost.")
    public String getLivenessCheckUrl() {
        return livenessCheckUrl;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "Whether liveness checking of the HTTP destination will take place. The queue property " +
            "must also be configured for liveness checking to happen.")
    public boolean isLivenessCheckEnabled() {
        return livenessCheckEnabled;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The API key to use when forwarding data if Stroom is configured to require an API key.")
    public String getApiKey() {
        return apiKey;
    }

    /**
     * If true, add Open ID authentication headers to the request. Only works if the identityProviderType
     * is EXTERNAL_IDP and the destination is in the same Open ID Connect realm as the OIDC client that this
     * proxy instance is using.
     */
    @JsonProperty
    public boolean isAddOpenIdAccessToken() {
        return addOpenIdAccessToken;
    }

    /**
     * Get the configuration for the HttpClient.
     */
    @JsonProperty("httpClient")
    public HttpClientConfiguration getHttpClient() {
        return httpClient;
    }

    @Override
    @NotNull // HTTP forwarder needs a queued mechanism to cope with failure
    @JsonProperty("queue")
    @JsonPropertyDescription("Adds multi-threading and retry control to this forwarder. " +
                             "This is required for a HTTP forwarder as requests may fail.")
    public ForwardHttpQueueConfig getForwardQueueConfig() {
        return forwardQueueConfig;
    }

    @Override
    public String getDestinationDescription(final DownstreamHostConfig downstreamHostConfig,
                                            final PathCreator ignored) {
        return createForwardUrl(downstreamHostConfig);
    }

    public String createForwardUrl(final DownstreamHostConfig downstreamHostConfig) {
        return downstreamHostConfig.createUri(forwardUrl, DEFAULT_FORWARD_PATH);
    }

    public String createLivenessCheckUrl(final DownstreamHostConfig downstreamHostConfig) {
        return downstreamHostConfig.createUri(livenessCheckUrl, DEFAULT_LIVENESS_CHECK_PATH);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardHttpPostConfig that = (ForwardHttpPostConfig) o;
        return enabled == that.enabled
               && instant == that.instant
               && addOpenIdAccessToken == that.addOpenIdAccessToken
               && Objects.equals(name, that.name)
               && Objects.equals(forwardUrl, that.forwardUrl)
               && Objects.equals(livenessCheckUrl, that.livenessCheckUrl)
               && livenessCheckEnabled == that.livenessCheckEnabled
               && Objects.equals(apiKey, that.apiKey)
               && Objects.equals(httpClient, that.httpClient)
               && Objects.equals(forwardQueueConfig, that.forwardQueueConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                instant,
                name,
                forwardUrl,
                livenessCheckUrl,
                livenessCheckEnabled,
                apiKey,
                addOpenIdAccessToken,
                httpClient,
                forwardQueueConfig);
    }

    @Override
    public String toString() {
        return "ForwardHttpPostConfig{" +
               "enabled=" + enabled +
               ", instant=" + instant +
               ", name='" + name + '\'' +
               ", forwardUrl='" + forwardUrl + '\'' +
               ", livenessCheckUrl='" + livenessCheckUrl + '\'' +
               ", livenessCheckEnabled=" + livenessCheckEnabled +
               ", apiKey='" + apiKey + '\'' +
               ", addOpenIdAccessToken=" + addOpenIdAccessToken +
               ", httpClient=" + httpClient +
               ", forwardQueueConfig=" + forwardQueueConfig +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class Builder {

        private Boolean enabled;
        private Boolean instant;
        private String name;
        private String forwardUrl;
        private String livenessCheckUrl;
        private Boolean livenessCheckEnabled;
        private String apiKey;
        private Boolean addOpenIdAccessToken;
        private HttpClientConfiguration httpClient;
        private ForwardHttpQueueConfig forwardQueueConfig;

        private Builder() {
            this(new ForwardHttpPostConfig());
        }

        private Builder(final ForwardHttpPostConfig forwardHttpPostConfig) {
            Objects.requireNonNull(forwardHttpPostConfig);
            this.enabled = forwardHttpPostConfig.enabled;
            this.instant = forwardHttpPostConfig.instant;
            this.name = forwardHttpPostConfig.name;
            this.forwardUrl = forwardHttpPostConfig.forwardUrl;
            this.livenessCheckUrl = forwardHttpPostConfig.livenessCheckUrl;
            this.livenessCheckEnabled = forwardHttpPostConfig.livenessCheckEnabled;
            this.apiKey = forwardHttpPostConfig.apiKey;
            this.addOpenIdAccessToken = forwardHttpPostConfig.addOpenIdAccessToken;
            this.httpClient = forwardHttpPostConfig.httpClient;
            this.forwardQueueConfig = forwardHttpPostConfig.forwardQueueConfig;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder instant(final boolean instant) {
            this.instant = instant;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder forwardUrl(final String forwardUrl) {
            this.forwardUrl = forwardUrl;
            return this;
        }

        public Builder livenessCheckUrl(final String livenessCheckUrl) {
            this.livenessCheckUrl = livenessCheckUrl;
            return this;
        }

        public Builder livenessCheckEnabled(final boolean livenessCheckEnabled) {
            this.livenessCheckEnabled = livenessCheckEnabled;
            return this;
        }

        public Builder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder addOpenIdAccessToken(final boolean addOpenIdAccessToken) {
            this.addOpenIdAccessToken = addOpenIdAccessToken;
            return this;
        }

        public Builder httpClient(final HttpClientConfiguration httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder forwardQueueConfig(final ForwardHttpQueueConfig forwardQueueConfig) {
            this.forwardQueueConfig = forwardQueueConfig;
            return this;
        }

        public ForwardHttpPostConfig build() {
            return new ForwardHttpPostConfig(
                    enabled,
                    instant,
                    name,
                    forwardUrl,
                    livenessCheckUrl,
                    livenessCheckEnabled,
                    apiKey,
                    addOpenIdAccessToken,
                    httpClient,
                    forwardQueueConfig);
        }
    }
}
