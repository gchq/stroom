package stroom.proxy.app.handler;

import stroom.util.http.HttpClientConfiguration;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public class ForwardHttpPostConfig extends AbstractConfig implements IsProxyConfig {

    public static final int INFINITE_RETRIES_VALUE = -1;
    public static final String PROP_NAME_ERROR_SUB_PATH_TEMPLATE = "errorSubPathTemplate";
    public static final TemplatingMode DEFAULT_TEMPLATING_MODE = TemplatingMode.REPLACE_UNKNOWN;
    private static final StroomDuration DEFAULT_FORWARD_DELAY = StroomDuration.ZERO;
    /**
     * Zero means no retries
     */
    private static final StroomDuration DEFAULT_MAX_RETRY_AGE = StroomDuration.ofDays(7);
    private static final int DEFAULT_RETRY_GROWTH_FACTOR = 1;
    private static final StroomDuration DEFAULT_RETRY_DELAY = StroomDuration.ofMinutes(10);
    private static final StroomDuration DEFAULT_MAX_RETRY_DELAY = StroomDuration.ofDays(1);
    private static final StroomDuration DEFAULT_FORWARD_TIMEOUT = StroomDuration.ofMinutes(1);
    private static final String DEFAULT_ERROR_PATH_TEMPLATE = "${year}${month}${day}/${feed}";

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String forwardUrl;
    private final String apiKey;
    private final StroomDuration forwardDelay;
    private final StroomDuration retryDelay;
    private final int retryDelayGrowthFactor;
    private final StroomDuration maxRetryDelay;
    private final StroomDuration maxRetryAge;
    private final boolean addOpenIdAccessToken;
    private final HttpClientConfiguration httpClient;
    private final String errorSubPathTemplate;
    private final TemplatingMode templatingMode;

    public ForwardHttpPostConfig() {
        enabled = true;
        instant = false;
        name = null;
        forwardUrl = null;
        apiKey = null;
        forwardDelay = DEFAULT_FORWARD_DELAY;
        retryDelay = DEFAULT_RETRY_DELAY;
        retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        maxRetryDelay = DEFAULT_RETRY_DELAY;
        maxRetryAge = DEFAULT_MAX_RETRY_AGE;
        addOpenIdAccessToken = false;
        httpClient = createDefaultHttpClientConfiguration();
        errorSubPathTemplate = DEFAULT_ERROR_PATH_TEMPLATE;
        templatingMode = DEFAULT_TEMPLATING_MODE;
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
                                 @JsonProperty("retryDelayGrowthFactor") final Integer retryDelayGrowthFactor,
                                 @JsonProperty("maxRetryDelay") final StroomDuration maxRetryDelay,
                                 @JsonProperty("maxRetryAge") final StroomDuration maxRetryAge,
                                 @JsonProperty("addOpenIdAccessToken") final boolean addOpenIdAccessToken,
                                 @JsonProperty("httpClient") final HttpClientConfiguration httpClient,
                                 @JsonProperty(PROP_NAME_ERROR_SUB_PATH_TEMPLATE) final String errorSubPathTemplate,
                                 @JsonProperty("templatingMode") final TemplatingMode templatingMode) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.forwardUrl = forwardUrl;
        this.apiKey = apiKey;
        this.forwardDelay = Objects.requireNonNullElse(forwardDelay, DEFAULT_FORWARD_DELAY);
        this.retryDelay = Objects.requireNonNullElse(retryDelay, DEFAULT_RETRY_DELAY);
        this.retryDelayGrowthFactor = Objects.requireNonNullElse(retryDelayGrowthFactor, DEFAULT_RETRY_GROWTH_FACTOR);
        this.maxRetryDelay = Objects.requireNonNullElse(maxRetryDelay, DEFAULT_MAX_RETRY_DELAY);
        this.maxRetryAge = Objects.requireNonNullElse(maxRetryAge, DEFAULT_MAX_RETRY_AGE);
        this.addOpenIdAccessToken = addOpenIdAccessToken;
        this.httpClient = Objects.requireNonNullElse(httpClient, createDefaultHttpClientConfiguration());
        this.errorSubPathTemplate = errorSubPathTemplate;
        this.templatingMode = templatingMode;
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
    @JsonPropertyDescription("The unique name of the destination (across all file/http forward destinations. " +
                             "The name is used in the directories on the file system, so do not change the name " +
                             "once proxy has processed data. Must be provided.")
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

    @JsonProperty
    @JsonPropertyDescription("If we fail to send, how long should we wait until we try again?")
    public StroomDuration getRetryDelay() {
        return retryDelay;
    }

    @JsonProperty
    @JsonPropertyDescription("If retryDelayGrowthFactor is > 1, " +
                             "this property controls the maximum retry delay interval.")
    public StroomDuration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * If we fail to send, how much to increase the retryDelay duration by after each retry failure,
     * e.g. 1.1 means increase by 10% each time,
     * i.e. if retryDelay is 1000 and retryDelayGrowthFactor is 1.1, then the retry delays will be
     * 1000, 1100, 1210, 1331, 1464, etc. Default value of 1 so the times don't increase.
     */
    @Min(1)
    @JsonProperty
    public int getRetryDelayGrowthFactor() {
        return retryDelayGrowthFactor;
    }

    @JsonProperty
    @JsonPropertyDescription("The maximum duration between the initial attempt and the last retry. Set to zero " +
                             "for no retires at all. Set to something large like 'PT99999999999999D' " +
                             "to 'always' retry.")
    public StroomDuration getMaxRetryAge() {
        return maxRetryAge;
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

    @JsonPropertyDescription("How to handle unknown parameters in the subPathTemplate. " +
                             "Default value is 'REPLACE_UNKNOWN'.")
    @JsonProperty
    public TemplatingMode getTemplatingMode() {
        return templatingMode;
    }

    @SuppressWarnings("unused") // Used by jakarta.validation
    @JsonIgnore
    @ValidationMethod(message = "maxRetryDelay must be greater than or equal to retryDelay")
    @Valid
    // Seems to be ignored if not prefixed with 'is'
    public boolean isMaxRetryDelayValid() {
        if (retryDelayGrowthFactor == 1) {
            return true;
        } else {
            return retryDelay != null
                   && maxRetryDelay != null
                   && maxRetryDelay.toMillis() >= retryDelay.toMillis();
        }
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
        return enabled == that.enabled && instant == that.instant
               && retryDelayGrowthFactor == that.retryDelayGrowthFactor
               && maxRetryAge == that.maxRetryAge && addOpenIdAccessToken == that.addOpenIdAccessToken
               && Objects.equals(
                name,
                that.name) && Objects.equals(forwardUrl, that.forwardUrl) && Objects.equals(apiKey,
                that.apiKey) && Objects.equals(forwardDelay, that.forwardDelay) && Objects.equals(
                retryDelay,
                that.retryDelay) && Objects.equals(maxRetryDelay, that.maxRetryDelay) && Objects.equals(
                httpClient,
                that.httpClient) && Objects.equals(errorSubPathTemplate,
                that.errorSubPathTemplate) && templatingMode == that.templatingMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                instant,
                name,
                forwardUrl,
                apiKey,
                forwardDelay,
                retryDelay,
                retryDelayGrowthFactor,
                maxRetryDelay,
                maxRetryAge,
                addOpenIdAccessToken,
                httpClient,
                errorSubPathTemplate,
                templatingMode);
    }

    @Override
    public String toString() {
        return "ForwardHttpPostConfig{" +
               "enabled=" + enabled +
               ", instant=" + instant +
               ", name='" + name + '\'' +
               ", forwardUrl='" + forwardUrl + '\'' +
               ", apiKey='" + apiKey + '\'' +
               ", forwardDelay=" + forwardDelay +
               ", retryDelay=" + retryDelay +
               ", retryDelayGrowthFactor=" + retryDelayGrowthFactor +
               ", maxRetryDelay=" + maxRetryDelay +
               ", maxRetryAge=" + maxRetryAge +
               ", addOpenIdAccessToken=" + addOpenIdAccessToken +
               ", httpClient=" + httpClient +
               ", errorSubPathTemplate='" + errorSubPathTemplate + '\'' +
               ", templatingMode=" + templatingMode +
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
        private StroomDuration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
        private int retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        private StroomDuration maxRetryAge = DEFAULT_MAX_RETRY_AGE;
        private boolean addOpenIdAccessToken;
        private HttpClientConfiguration httpClient;
        private String errorSubPathTemplate;
        private TemplatingMode templatingMode;

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
            this.maxRetryDelay = forwardHttpPostConfig.maxRetryDelay;
            this.retryDelayGrowthFactor = forwardHttpPostConfig.retryDelayGrowthFactor;
            this.maxRetryAge = forwardHttpPostConfig.maxRetryAge;
            this.addOpenIdAccessToken = forwardHttpPostConfig.addOpenIdAccessToken;
            this.httpClient = forwardHttpPostConfig.httpClient;
            this.errorSubPathTemplate = forwardHttpPostConfig.errorSubPathTemplate;
            this.templatingMode = forwardHttpPostConfig.templatingMode;
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

        public Builder maxRetryDelay(final StroomDuration maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        public Builder retryDelayGrowthFactor(final int retryDelayGrowthFactor) {
            this.retryDelayGrowthFactor = retryDelayGrowthFactor;
            return this;
        }

        public Builder maxRetryAge(final StroomDuration maxRetryAge) {
            this.maxRetryAge = maxRetryAge;
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

        public Builder withTemplatingMode(final TemplatingMode templatingMode) {
            this.templatingMode = templatingMode;
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
                    retryDelayGrowthFactor,
                    maxRetryDelay,
                    maxRetryAge,
                    addOpenIdAccessToken,
                    httpClient,
                    errorSubPathTemplate,
                    templatingMode);
        }
    }
}
