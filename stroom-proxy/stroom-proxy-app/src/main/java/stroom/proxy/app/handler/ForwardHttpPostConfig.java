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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public class ForwardHttpPostConfig extends AbstractConfig implements IsProxyConfig {

    public static final int INFINITE_RETRIES_VALUE = -1;
    public static final String PROP_NAME_ERROR_SUB_PATH_TEMPLATE = "errorSubPathTemplate";
    private static final StroomDuration DEFAULT_FORWARD_DELAY = StroomDuration.ZERO;
    /**
     * null means infinite retries
     */
    private static final int DEFAULT_MAX_RETRIES = INFINITE_RETRIES_VALUE;
    private static final int DEFAULT_RETRY_GROWTH_FACTOR = 1;
    private static final StroomDuration DEFAULT_RETRY_DELAY = StroomDuration.ofSeconds(10);
    private static final StroomDuration DEFAULT_FORWARD_TIMEOUT = StroomDuration.ofMinutes(1);
    private static final String DEFAULT_ERROR_PATH_TEMPLATE = "${feed}";

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String forwardUrl;
    private final String apiKey;
    private final StroomDuration forwardDelay;
    private final StroomDuration retryDelay;
    //    private final int retryDelayGrowthFactor;
    private final int maxRetries;
    private final boolean addOpenIdAccessToken;
    private final HttpClientConfiguration httpClient;
    private final String errorSubPathTemplate;

    public ForwardHttpPostConfig() {
        enabled = true;
        instant = false;
        name = null;
        forwardUrl = null;
        apiKey = null;
        forwardDelay = DEFAULT_FORWARD_DELAY;
        retryDelay = DEFAULT_RETRY_DELAY;
//        retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        maxRetries = DEFAULT_MAX_RETRIES;
        addOpenIdAccessToken = false;
        httpClient = createDefaultHttpClientConfiguration();
        errorSubPathTemplate = DEFAULT_ERROR_PATH_TEMPLATE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpPostConfig(@JsonProperty("enabled") final boolean enabled,
                                 @JsonProperty("instant") final boolean instant,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("forwardUrl") final String forwardUrl,
                                 @JsonProperty("apiKey") final String apiKey,
                                 @JsonProperty("forwardDelay") final StroomDuration forwardDelay,
                                 @JsonProperty("retryDelay") final StroomDuration retryDelay,
//                                 @JsonProperty("retryDelayGrowthFactor") final Integer retryDelayGrowthFactor,
                                 @JsonProperty("maxRetries") final Integer maxRetries,
                                 @JsonProperty("addOpenIdAccessToken") final boolean addOpenIdAccessToken,
                                 @JsonProperty("httpClient") final HttpClientConfiguration httpClient,
                                 @JsonProperty(PROP_NAME_ERROR_SUB_PATH_TEMPLATE) final String errorSubPathTemplate) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.forwardUrl = forwardUrl;
        this.apiKey = apiKey;
        this.forwardDelay = Objects.requireNonNullElse(forwardDelay, DEFAULT_FORWARD_DELAY);
        this.retryDelay = Objects.requireNonNullElse(retryDelay, DEFAULT_RETRY_DELAY);
//        this.retryDelayGrowthFactor = Objects.requireNonNullElse(retryDelayGrowthFactor, DEFAULT_RETRY_GROWTH_FACTOR);
        this.maxRetries = Objects.requireNonNullElse(maxRetries, DEFAULT_MAX_RETRIES);
        this.addOpenIdAccessToken = addOpenIdAccessToken;
        this.httpClient = Objects.requireNonNullElse(httpClient, createDefaultHttpClientConfiguration());
        this.errorSubPathTemplate = errorSubPathTemplate;
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
    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Should data be forwarded instantly during the receipt process, i.e. must we" +
                             " successfully forward before returning a success response to the sender.")
    public boolean isInstant() {
        return instant;
    }

    @NotNull
    @JsonProperty
    public String getName() {
        return name;
    }

    /**
     * The URLs to forward onto. This is pass-through mode if instant is set.
     */
    @NotNull
    @JsonProperty
    public String getForwardUrl() {
        return forwardUrl;
    }

    /**
     * The API key to use when forwarding data if Stroom is configured to require an API key.
     */
    @NotNull
    @JsonProperty
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Debug setting to add a delay
     */
    @JsonProperty
    public StroomDuration getForwardDelay() {
        return forwardDelay;
    }

    /**
     * If we fail to send, how long should we wait until we try again?
     */
    @JsonProperty
    public StroomDuration getRetryDelay() {
        return retryDelay;
    }

    /**
     * If we fail to send, how much to increase the retryDelay duration by after each retry failure,
     * e.g. 1.1 means increase by 10% each time. Default value of 1.
     */
    @Min(1)
    @JsonProperty
    public static int getDefaultRetryGrowthFactor() {
        return DEFAULT_RETRY_GROWTH_FACTOR;
    }

    /**
     * How many times should we try to send the same data?
     * A null value means infinite retries.
     */
    @Min(-1)
    @JsonProperty
    public int getMaxRetries() {
        return maxRetries;
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

    /**
     * The template to use to create subdirectories of the error destination directory.
     * The error directory is used when the retry limit is reached
     * or the data is explicitly rejected by the downstream proxy/stroom.
     * Must be a relative path.
     * Supported template parameters (must be lower-case) are:
     * <ul>
     *     <li><code>${feed}</code></li>
     *     <li><code>${type}</code></li>
     *     <li><code>${year}</code></li>
     *     <li><code>${month}</code></li>
     *     <li><code>${day}</code></li>
     *     <li><code>${hour}</code></li>
     *     <li><code>${minute}</code></li>
     *     <li><code>${second}</code></li>
     *     <li><code>${millis}</code></li>
     *     <li><code>${ms}</code></li>
     * </ul>
     */
    @Pattern(regexp = "^[^/].*$") // Relative paths only
    @JsonProperty
    public String getErrorSubPathTemplate() {
        return errorSubPathTemplate;
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
               && maxRetries == that.maxRetries
               && addOpenIdAccessToken == that.addOpenIdAccessToken
               && Objects.equals(name, that.name)
               && Objects.equals(forwardUrl, that.forwardUrl)
               && Objects.equals(apiKey, that.apiKey)
               && Objects.equals(forwardDelay, that.forwardDelay)
               && Objects.equals(retryDelay, that.retryDelay)
               && Objects.equals(httpClient, that.httpClient)
               && Objects.equals(errorSubPathTemplate, that.errorSubPathTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                instant,
                name,
                forwardUrl,
                apiKey,
                forwardDelay,
                retryDelay,
                maxRetries,
                addOpenIdAccessToken,
                httpClient,
                errorSubPathTemplate);
    }

    @Override
    public String toString() {
        return "ForwardHttpPostConfig{" +
               "enabled=" + enabled +
               ", instant=" + instant +
               ", name='" + name + '\'' +
               ", forwardUrl='" + forwardUrl + '\'' +
               ", forwardDelay=" + forwardDelay +
               ", retryDelay=" + retryDelay +
               ", maxRetries=" + maxRetries +
               ", addOpenIdAccessToken=" + addOpenIdAccessToken +
               ", httpClientConfiguration=" + httpClient +
               ", errorSubPathTemplate=" + errorSubPathTemplate +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private boolean instant;
        private String name;
        private String forwardUrl;
        private String apiKey;
        private StroomDuration forwardDelay = DEFAULT_FORWARD_DELAY;
        private StroomDuration retryDelay = DEFAULT_RETRY_DELAY;
        private Integer maxRetries = DEFAULT_MAX_RETRIES;
        private boolean addOpenIdAccessToken;
        private HttpClientConfiguration httpClient;
        private String errorSubPathTemplate;

        private Builder() {
            this(new ForwardHttpPostConfig());
        }

        private Builder(final ForwardHttpPostConfig forwardHttpPostConfig) {
            Objects.requireNonNull(forwardHttpPostConfig);
            this.enabled = forwardHttpPostConfig.enabled;
            this.instant = forwardHttpPostConfig.instant;
            this.name = forwardHttpPostConfig.name;
            this.forwardUrl = forwardHttpPostConfig.forwardUrl;
            this.apiKey = forwardHttpPostConfig.apiKey;
            this.forwardDelay = forwardHttpPostConfig.forwardDelay;
            this.retryDelay = forwardHttpPostConfig.retryDelay;
            this.maxRetries = forwardHttpPostConfig.maxRetries;
            this.addOpenIdAccessToken = forwardHttpPostConfig.addOpenIdAccessToken;
            this.httpClient = forwardHttpPostConfig.httpClient;
            this.errorSubPathTemplate = forwardHttpPostConfig.errorSubPathTemplate;
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

        public Builder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder forwardDelay(final StroomDuration forwardDelay) {
            this.forwardDelay = forwardDelay;
            return this;
        }

        public Builder retryDelay(final StroomDuration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder maxRetries(final Integer maxRetries) {
            this.maxRetries = maxRetries;
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

        public Builder errorSubPathTemplate(final String errorSubPathTemplate) {
            this.errorSubPathTemplate = errorSubPathTemplate;
            return this;
        }

        public ForwardHttpPostConfig build() {
            return new ForwardHttpPostConfig(
                    enabled,
                    instant,
                    name,
                    forwardUrl,
                    apiKey,
                    forwardDelay,
                    retryDelay,
                    maxRetries,
                    addOpenIdAccessToken,
                    httpClient,
                    errorSubPathTemplate);
        }
    }
}
