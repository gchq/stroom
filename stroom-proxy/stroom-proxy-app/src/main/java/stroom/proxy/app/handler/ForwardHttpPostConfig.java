package stroom.proxy.app.handler;

import stroom.util.http.HttpClientConfiguration;
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

    private static final StroomDuration DEFAULT_FORWARD_DELAY = StroomDuration.ZERO;
    private static final StroomDuration DEFAULT_FORWARD_TIMEOUT = StroomDuration.ofMinutes(1);

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String forwardUrl;
    private final String livenessCheckUrl;
    private final String apiKey;
    private final StroomDuration forwardDelay;
    private final boolean addOpenIdAccessToken;
    private final HttpClientConfiguration httpClient;
    private final ForwardQueueConfig forwardQueueConfig;

    public ForwardHttpPostConfig() {
        enabled = true;
        instant = false;
        name = null;
        forwardUrl = null;
        livenessCheckUrl = null;
        apiKey = null;
        forwardDelay = DEFAULT_FORWARD_DELAY;
        addOpenIdAccessToken = false;
        httpClient = createDefaultHttpClientConfiguration();
        forwardQueueConfig = new ForwardQueueConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpPostConfig(@JsonProperty("enabled") final boolean enabled,
                                 @JsonProperty("instant") final boolean instant,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("forwardUrl") final String forwardUrl,
                                 @JsonProperty("livenessCheckUrl") final String livenessCheckUrl,
                                 @JsonProperty("apiKey") final String apiKey,
                                 @JsonProperty("forwardDelay") final StroomDuration forwardDelay,
                                 @JsonProperty("addOpenIdAccessToken") final boolean addOpenIdAccessToken,
                                 @JsonProperty("httpClient") final HttpClientConfiguration httpClient,
                                 @JsonProperty("queue") final ForwardQueueConfig forwardQueueConfig) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.forwardUrl = forwardUrl;
        this.livenessCheckUrl = livenessCheckUrl;
        this.apiKey = apiKey;
        this.forwardDelay = Objects.requireNonNullElse(forwardDelay, DEFAULT_FORWARD_DELAY);
        this.addOpenIdAccessToken = addOpenIdAccessToken;
        this.httpClient = Objects.requireNonNullElse(httpClient, createDefaultHttpClientConfiguration());
        this.forwardQueueConfig = forwardQueueConfig;
    }

    private HttpClientConfiguration createDefaultHttpClientConfiguration() {
        return HttpClientConfiguration
                .builder()
                .timeout(DEFAULT_FORWARD_TIMEOUT)
                .connectionTimeout(DEFAULT_FORWARD_TIMEOUT)
                .connectionRequestTimeout(DEFAULT_FORWARD_TIMEOUT)
                .timeToLive(DEFAULT_FORWARD_TIMEOUT)
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
    @JsonPropertyDescription("The URL to forward onto. This is pass-through mode if instant is set.")
    public String getForwardUrl() {
        return forwardUrl;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "The URL to check for liveness of the forward destination. The URL should return a 200 response " +
            "to a GET request for the destination to be considered live. If null, no liveness check will be " +
            "made and the destination will be assumed to be live. If the response is not a 200, forwarding " +
            "will be paused at least until the next liveness check is performed.")
    public String getLivenessUrl() {
        return livenessCheckUrl;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The API key to use when forwarding data if Stroom is configured to require an API key.")
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty
    @JsonPropertyDescription("Debug setting to add a delay")
    public StroomDuration getForwardDelay() {
        return forwardDelay;
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
    public ForwardQueueConfig getForwardQueueConfig() {
        return forwardQueueConfig;
    }

    public static Builder builder() {
        return new Builder();
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
               && Objects.equals(apiKey, that.apiKey)
               && Objects.equals(forwardDelay, that.forwardDelay)
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
                apiKey,
                forwardDelay,
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
               ", apiKey='" + apiKey + '\'' +
               ", forwardDelay=" + forwardDelay +
               ", addOpenIdAccessToken=" + addOpenIdAccessToken +
               ", httpClient=" + httpClient +
               ", forwardQueueConfig=" + forwardQueueConfig +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private boolean instant;
        private String name;
        private String forwardUrl;
        private String livenessCheckUrl;
        private String apiKey;
        private StroomDuration forwardDelay = DEFAULT_FORWARD_DELAY;
        private boolean addOpenIdAccessToken;
        private HttpClientConfiguration httpClient;
        private ForwardQueueConfig forwardQueueConfig;

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
            this.apiKey = forwardHttpPostConfig.apiKey;
            this.forwardDelay = forwardHttpPostConfig.forwardDelay;
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

        public Builder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder forwardDelay(final StroomDuration forwardDelay) {
            this.forwardDelay = forwardDelay;
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

        public Builder forwardQueueConfig(final ForwardQueueConfig forwardQueueConfig) {
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
                    apiKey,
                    forwardDelay,
                    addOpenIdAccessToken,
                    httpClient,
                    forwardQueueConfig);
        }
    }
}
