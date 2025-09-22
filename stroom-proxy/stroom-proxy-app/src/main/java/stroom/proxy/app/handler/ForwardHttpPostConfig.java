package stroom.proxy.app.handler;

import stroom.util.collections.CollectionUtil;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public final class ForwardHttpPostConfig
        extends AbstractConfig
        implements IsProxyConfig, ForwarderConfig {

    private static final StroomDuration DEFAULT_FORWARD_DELAY = StroomDuration.ZERO;
    private static final StroomDuration DEFAULT_RETRY_DELAY = StroomDuration.ofSeconds(10);
    private static final StroomDuration DEFAULT_FORWARD_TIMEOUT = StroomDuration.ofMinutes(1);
    private static final Integer DEFAULT_MAX_RETRIES = 3;

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String forwardUrl;
    private final String livenessCheckUrl;
    private final String apiKey;
    private final boolean addOpenIdAccessToken;
    private final HttpClientConfiguration httpClient;
    private final ForwardHttpQueueConfig forwardQueueConfig;
    private final Set<String> forwardHeadersAdditionalAllowSet;

    public ForwardHttpPostConfig() {
        enabled = true;
        instant = false;
        name = null;
        forwardUrl = null;
        livenessCheckUrl = null;
        apiKey = null;
        addOpenIdAccessToken = false;
        httpClient = createDefaultHttpClientConfiguration();
        forwardQueueConfig = new ForwardHttpQueueConfig();
        forwardHeadersAdditionalAllowSet = Collections.emptySet();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpPostConfig(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("instant") final boolean instant,
            @JsonProperty("name") final String name,
            @JsonProperty("forwardUrl") final String forwardUrl,
            @JsonProperty("livenessCheckUrl") final String livenessCheckUrl,
            @JsonProperty("apiKey") final String apiKey,
            @JsonProperty("addOpenIdAccessToken") final boolean addOpenIdAccessToken,
            @JsonProperty("httpClient") final HttpClientConfiguration httpClient,
            @JsonProperty("queue") final ForwardHttpQueueConfig forwardQueueConfig,
            @JsonProperty("forwardHeadersAdditionalAllowSet") final Set<String> forwardHeadersAdditionalAllowSet) {

        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.forwardUrl = forwardUrl;
        this.livenessCheckUrl = livenessCheckUrl;
        this.apiKey = apiKey;
        this.addOpenIdAccessToken = addOpenIdAccessToken;
        this.httpClient = Objects.requireNonNullElse(httpClient, createDefaultHttpClientConfiguration());
        this.forwardQueueConfig = Objects.requireNonNullElseGet(forwardQueueConfig, ForwardHttpQueueConfig::new);
        this.forwardHeadersAdditionalAllowSet = NullSafe.unmodifialbeSet(forwardHeadersAdditionalAllowSet);
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
    @JsonPropertyDescription("The URL to forward onto. This is pass-through mode if instant is set.")
    public String getForwardUrl() {
        return forwardUrl;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "The URL to check for liveness of the forward destination. The URL should return a 200 response " +
            "to a GET request for the destination to be considered live. If null, empty or property 'queue' " +
            "is not configured then no liveness check will be " +
            "made and the destination will be assumed to be live. If the response is not a 200, forwarding " +
            "will be paused at least until the next liveness check is performed.")
    public String getLivenessCheckUrl() {
        return livenessCheckUrl;
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

    @JsonProperty
    @JsonPropertyDescription("Set of HTTP headers that should be added to the request when proxy forwards data. " +
                             "THis set is in addition to the base set of allowed headers.")
    public Set<String> getForwardHeadersAdditionalAllowSet() {
        return forwardHeadersAdditionalAllowSet;
    }

    @JsonIgnore
    @Override
    public String getDestinationDescription() {
        return forwardUrl;
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
               ", apiKey='" + apiKey + '\'' +
               ", addOpenIdAccessToken=" + addOpenIdAccessToken +
               ", httpClient=" + httpClient +
               ", forwardQueueConfig=" + forwardQueueConfig +
               '}';
    }

    private static Set<String> normaliseFields(final Set<String> fields) {
        return CollectionUtil.cleanItems(fields, s -> s.trim().toLowerCase());
    }

    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private boolean instant;
        private String name;
        private String forwardUrl;
        private String livenessCheckUrl;
        private String apiKey;
        private boolean addOpenIdAccessToken;
        private HttpClientConfiguration httpClient;
        private ForwardHttpQueueConfig forwardQueueConfig;
        private Set<String> forwardHeadersAdditionalAllowSet;

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
            this.addOpenIdAccessToken = forwardHttpPostConfig.addOpenIdAccessToken;
            this.httpClient = forwardHttpPostConfig.httpClient;
            this.forwardQueueConfig = forwardHttpPostConfig.forwardQueueConfig;
            this.forwardHeadersAdditionalAllowSet = forwardHttpPostConfig.forwardHeadersAdditionalAllowSet;
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

        public Builder forwardHeadersAdditionalAllowSet(final Set<String> forwardHeadersAdditionalAllowSet) {
            if (this.forwardHeadersAdditionalAllowSet == null) {
                this.forwardHeadersAdditionalAllowSet = new HashSet<>();
            }
            NullSafe.stream(forwardHeadersAdditionalAllowSet)
                    .filter(NullSafe::isNonBlankString)
                    .forEach(header ->
                            this.forwardHeadersAdditionalAllowSet.add(header));
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
                    addOpenIdAccessToken,
                    httpClient,
                    forwardQueueConfig,
                    forwardHeadersAdditionalAllowSet);
        }
    }
}
