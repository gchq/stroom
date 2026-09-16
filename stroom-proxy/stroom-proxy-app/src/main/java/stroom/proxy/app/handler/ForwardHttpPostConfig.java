/*
 * Copyright 2022 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.receive.common.ReceiveDataServlet;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.io.PathCreator;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    public static final boolean DEFAULT_ADD_OPEN_ID_ACCESS_TOKEN = false;
    public static final boolean DEFAULT_IS_ENABLED = true;
    public static final boolean DEFAULT_LIVENESS_CHECK_ENABLED = true;
    public static final boolean DEFAULT_IS_INSTANT = false;
    /** Five, as the forward thread count was before the retry tier went. */
    public static final ConsumerStageThreadsConfig DEFAULT_THREADS = new ConsumerStageThreadsConfig(5);

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
    private final ForwardRetryConfig retry;
    private final FailureDestinationConfig failureDestination;
    private final ConsumerStageThreadsConfig threads;
    private final Set<String> forwardHeadersAdditionalAllowSet;

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
        retry = new ForwardRetryConfig();
        failureDestination = null;
        threads = DEFAULT_THREADS;
        forwardHeadersAdditionalAllowSet = Collections.emptySet();
    }

    @SuppressWarnings({"unused", "checkstyle:linelength"})
    @JsonCreator
    public ForwardHttpPostConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("instant") final Boolean instant,
            @JsonProperty("name") final String name,
            @JsonProperty("forwardUrl") final String forwardUrl,
            @JsonProperty("livenessCheckUrl") final String livenessCheckUrl,
            @JsonProperty("livenessCheckEnabled") final Boolean livenessCheckEnabled,
            @JsonProperty("apiKey") final String apiKey,
            @JsonProperty("addOpenIdAccessToken") final Boolean addOpenIdAccessToken,
            @JsonProperty("httpClient") final HttpClientConfiguration httpClient,
            @JsonProperty("retry") final ForwardRetryConfig retry,
            @JsonProperty("failureDestination") final FailureDestinationConfig failureDestination,
            @JsonProperty("threads") final ConsumerStageThreadsConfig threads,
            @JsonProperty("forwardHeadersAdditionalAllowSet") final Set<String> forwardHeadersAdditionalAllowSet) {

        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_IS_ENABLED);
        this.instant = Objects.requireNonNullElse(instant, DEFAULT_IS_INSTANT);
        this.name = name;
        this.forwardUrl = forwardUrl;
        this.livenessCheckUrl = livenessCheckUrl;
        this.livenessCheckEnabled = Objects.requireNonNullElse(livenessCheckEnabled, DEFAULT_LIVENESS_CHECK_ENABLED);
        this.apiKey = apiKey;
        this.addOpenIdAccessToken = Objects.requireNonNullElse(addOpenIdAccessToken, DEFAULT_ADD_OPEN_ID_ACCESS_TOKEN);
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::createDefaultHttpClientConfiguration);
        this.retry = Objects.requireNonNullElseGet(retry, ForwardRetryConfig::new);
        // Null means the default: <data>/50_forwarding/<name>/03_failure in the proxy's data directory.
        this.failureDestination = failureDestination;
        this.threads = Objects.requireNonNullElse(threads, DEFAULT_THREADS);
        this.forwardHeadersAdditionalAllowSet = NullSafe.unmodifialbeSet(forwardHeadersAdditionalAllowSet);
    }

    private HttpClientConfiguration createDefaultHttpClientConfiguration() {
        return HttpClientConfiguration
                .builder()
                .timeout(HttpClientConfiguration.DEFAULT_TIMEOUT)
                .connectionTimeout(HttpClientConfiguration.DEFAULT_CONNECTION_TIMEOUT)
                .connectionRequestTimeout(HttpClientConfiguration.DEFAULT_CONNECTION_REQUEST_TIMEOUT)
                .timeToLive(HttpClientConfiguration.DEFAULT_TIME_TO_LIVE)
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
    @JsonPropertyDescription("The unique name of the destination, across file, HTTP and S3 forward destinations. " +
                             "It names the destination's default give-up directory and, when more than one " +
                             "destination is enabled, its forward-<name> queue and file store, so do not change " +
                             "it once the proxy has processed data. Must be provided.")
    public String getName() {
        return name;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "The URL/path to forward on to. " +
            "If not set, the downstreamHost configuration is combined with the default API path (/datafeed). " +
            "If this property is just a path, it is combined with the downstreamHost configuration. " +
            "Only set this property if you wish to use a non-default path " +
            "or a different host/port/scheme to that defined in downstreamHost.")
    public String getForwardUrl() {
        return forwardUrl;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "The URL/path to check for liveness of the forward destination. The URL should return a 200 response " +
            "to a GET request for the destination to be considered live. " +
            "If the response from the liveness check is not a 200, forwarding " +
            "will be paused at least until the next liveness check is performed. " +
            "If not set, the downstreamHost configuration is combined with the default API path (/status). " +
            "If this property is just a path, it is combined with the downstreamHost configuration. " +
            "Only set this property if you wish to use a non-default path " +
            "or a different host/port/scheme to that defined in downstreamHost.")
    public String getLivenessCheckUrl() {
        return livenessCheckUrl;
    }

    @JsonProperty
    @JsonPropertyDescription(
            "Whether liveness checking of the HTTP destination will take place, every " +
            "retry.livenessCheckInterval. Forwarding to this destination is paused while the check fails.")
    public boolean isLivenessCheckEnabled() {
        return livenessCheckEnabled;
    }

    @JsonProperty
    @JsonPropertyDescription("The API key to use when forwarding data if Stroom is configured to require an API key.")
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty
    @JsonPropertyDescription("If true, an Open ID access token is added to each forward request. Only works " +
                             "when identityProviderType is EXTERNAL_IDP and the destination is in the same Open " +
                             "ID Connect realm as the client this proxy uses.")
    public boolean isAddOpenIdAccessToken() {
        return addOpenIdAccessToken;
    }

    @JsonProperty("httpClient")
    @JsonPropertyDescription("The HTTP client used to post to this destination: timeouts, TLS, proxy, " +
                             "connection limits.")
    public HttpClientConfiguration getHttpClient() {
        return httpClient;
    }

    @Override
    @NotNull
    @JsonProperty("retry")
    @JsonPropertyDescription("How this destination retries a group it could not deliver and when it gives up.")
    public ForwardRetryConfig getRetry() {
        return retry;
    }

    @Override
    @JsonProperty("failureDestination")
    @JsonPropertyDescription("Where this destination writes data it has given up on, with an error.log " +
                             "beside each group. Configured independently of where it forwards to, so an " +
                             "HTTP forwarder can quarantine to a directory or to S3. When unset, a " +
                             "'03_failure' directory under 50_forwarding/<name> in the proxy's data directory is " +
                             "used.")
    public FailureDestinationConfig getFailureDestination() {
        return failureDestination;
    }

    @Override
    @NotNull
    @JsonProperty("threads")
    @JsonPropertyDescription("How many threads deliver to this destination.")
    public ConsumerStageThreadsConfig getThreads() {
        return threads;
    }

    @JsonProperty
    @JsonPropertyDescription("Set of HTTP headers that should be added to the request when proxy forwards data. " +
                             "This set is in addition to the base set of allowed headers.")
    public Set<String> getForwardHeadersAdditionalAllowSet() {
        return forwardHeadersAdditionalAllowSet;
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
        // ForwardHeadersAdditionalAllowSet is a live builder field that widens which headers are
        // forwarded - a security-relevant setting - and it was in none of these three, so two configs
        // differing only in it compared equal.
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
               && Objects.equals(retry, that.retry)
               && Objects.equals(failureDestination, that.failureDestination)
               && Objects.equals(threads, that.threads)
               && Objects.equals(forwardHeadersAdditionalAllowSet, that.forwardHeadersAdditionalAllowSet);
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
                retry,
                failureDestination,
                threads,
                forwardHeadersAdditionalAllowSet);
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
               ", retry=" + retry +
               ", failureDestination=" + failureDestination +
               ", threads=" + threads +
               ", forwardHeadersAdditionalAllowSet=" + forwardHeadersAdditionalAllowSet +
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
        private ForwardRetryConfig retry;
        private FailureDestinationConfig failureDestination;
        private ConsumerStageThreadsConfig threads;
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
            this.livenessCheckEnabled = forwardHttpPostConfig.livenessCheckEnabled;
            this.apiKey = forwardHttpPostConfig.apiKey;
            this.addOpenIdAccessToken = forwardHttpPostConfig.addOpenIdAccessToken;
            this.httpClient = forwardHttpPostConfig.httpClient;
            this.retry = forwardHttpPostConfig.retry;
            this.failureDestination = forwardHttpPostConfig.failureDestination;
            this.threads = forwardHttpPostConfig.threads;
            this.forwardHeadersAdditionalAllowSet = NullSafe.mutableSet(
                    forwardHttpPostConfig.forwardHeadersAdditionalAllowSet);
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

        public Builder retry(final ForwardRetryConfig retry) {
            this.retry = retry;
            return this;
        }

        public Builder failureDestination(final FailureDestinationConfig failureDestination) {
            this.failureDestination = failureDestination;
            return this;
        }

        public Builder threads(final ConsumerStageThreadsConfig threads) {
            this.threads = threads;
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
                    livenessCheckEnabled,
                    apiKey,
                    addOpenIdAccessToken,
                    httpClient,
                    retry,
                    failureDestination,
                    threads,
                    forwardHeadersAdditionalAllowSet);
        }
    }
}
