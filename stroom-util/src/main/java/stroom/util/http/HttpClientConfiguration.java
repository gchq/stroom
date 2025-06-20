package stroom.util.http;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;


/**
 * This class is essentially a copy of {@link io.dropwizard.client.HttpClientConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method.
 * All uses of {@link io.dropwizard.util.Duration} have been replaced with {@link StroomDuration}
 * for consistency with the rest of the config.
 * Values are extracted from this using reflection by {@link stroom.util.http.HttpClientConfigConverter} so it is
 * key that the method names match.
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class HttpClientConfiguration extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String PROP_NAME_TLS = "tls";

    @NotNull
    private final StroomDuration timeout;

    @NotNull
    private final StroomDuration connectionTimeout;

    @NotNull
    private final StroomDuration connectionRequestTimeout;

    @NotNull
    private final StroomDuration timeToLive;

    private final boolean cookiesEnabled;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private final int maxConnections;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private final int maxConnectionsPerRoute;

    @NotNull
    private final StroomDuration keepAlive;

    @Min(0)
    @Max(1000)
    private final int retries;

    // Changed this to be a string rather than an optional to avoid serialisation issues when
    // we merge our config.yml node tree with a default node tree and then serialise for drop wiz to
    // read.
    private final String userAgent;

    private final HttpProxyConfiguration proxyConfiguration;

    @NotNull
    private final StroomDuration validateAfterInactivityPeriod;

    //    @Valid
    private final HttpTlsConfiguration tlsConfiguration;


    public HttpClientConfiguration() {
        timeout = StroomDuration.ofMinutes(3);
        connectionTimeout = StroomDuration.ofMinutes(3);
        connectionRequestTimeout = StroomDuration.ofMinutes(3);
        timeToLive = StroomDuration.ofHours(1);
        cookiesEnabled = false;
        maxConnections = 1024;
        maxConnectionsPerRoute = 1024;
        keepAlive = StroomDuration.ZERO;
        retries = 0;
        userAgent = null;
        proxyConfiguration = null;
        validateAfterInactivityPeriod = StroomDuration.ZERO;
        tlsConfiguration = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public HttpClientConfiguration(
            @JsonProperty("timeout") final StroomDuration timeout,
            @JsonProperty("connectionTimeout") final StroomDuration connectionTimeout,
            @JsonProperty("connectionRequestTimeout") final StroomDuration connectionRequestTimeout,
            @JsonProperty("timeToLive") final StroomDuration timeToLive,
            @JsonProperty("cookiesEnabled") final boolean cookiesEnabled,
            @JsonProperty("maxConnections") final Integer maxConnections,
            @JsonProperty("maxConnectionsPerRoute") final Integer maxConnectionsPerRoute,
            @JsonProperty("keepAlive") final StroomDuration keepAlive,
            @JsonProperty("retries") final int retries,
            @JsonProperty("userAgent") final String userAgent,
            @JsonProperty("proxy") final HttpProxyConfiguration proxyConfiguration,
            @JsonProperty("validateAfterInactivityPeriod") final StroomDuration validateAfterInactivityPeriod,
            @Nullable @JsonProperty(PROP_NAME_TLS) final HttpTlsConfiguration tlsConfiguration) {

        this.timeout = Objects
                .requireNonNullElse(timeout, StroomDuration.ofMillis(500));
        this.connectionTimeout = Objects
                .requireNonNullElse(connectionTimeout, StroomDuration.ofMillis(500));
        this.connectionRequestTimeout = Objects
                .requireNonNullElse(connectionRequestTimeout, StroomDuration.ofMillis(500));
        this.timeToLive = Objects
                .requireNonNullElse(timeToLive, StroomDuration.ofHours(1));
        this.cookiesEnabled = cookiesEnabled;
        this.maxConnections = Objects
                .requireNonNullElse(maxConnections, 1024);
        this.maxConnectionsPerRoute = Objects
                .requireNonNullElse(maxConnectionsPerRoute, 1024);
        this.keepAlive = Objects
                .requireNonNullElse(keepAlive, StroomDuration.ZERO);
        this.retries = retries;
        this.userAgent = userAgent;
        this.proxyConfiguration = proxyConfiguration;
        this.validateAfterInactivityPeriod = Objects
                .requireNonNullElse(validateAfterInactivityPeriod, StroomDuration.ZERO);
        this.tlsConfiguration = tlsConfiguration;
    }

    @JsonPropertyDescription("The maximum time a connection will be kept alive before it is reconnected. " +
                             "If set to 0, connections will be immediately closed after every request/response. " +
                             "Default: 0")
    @JsonProperty
    public StroomDuration getKeepAlive() {
        return keepAlive;
    }

    @JsonProperty
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    @JsonPropertyDescription("Determines the timeout until arrival of a response from the opposite endpoint. " +
                             "A timeout value of zero is interpreted as an infinite timeout. " +
                             "Default: 3 minutes")
    @JsonProperty
    public StroomDuration getTimeout() {
        return timeout;
    }

    @JsonPropertyDescription("Determines the timeout until a new connection is fully established. " +
                             "This may also include transport security negotiation exchanges such as SSL or TLS " +
                             "protocol negotiation. " +
                             "A timeout value of zero is interpreted as an infinite timeout. " +
                             "Default: 3 minutes")
    @JsonProperty
    public StroomDuration getConnectionTimeout() {
        return connectionTimeout;
    }

    @JsonPropertyDescription("Returns the connection lease request timeout used when requesting a connection from " +
                             "the connection manager. " +
                             "Default: 3 minutes")
    @JsonProperty
    public StroomDuration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    @JsonPropertyDescription("The maximum time a pooled connection can stay idle (not leased to any thread) " +
                             "before it is shut down. " +
                             "Default: 1 hour")
    @JsonProperty
    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    @JsonProperty
    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    @JsonProperty
    public int getMaxConnections() {
        return maxConnections;
    }

    @JsonProperty
    public int getRetries() {
        return retries;
    }

    @JsonProperty
    public String getUserAgent() {
        return userAgent;
    }

    @JsonProperty("proxy")
    @Nullable
    public HttpProxyConfiguration getProxyConfiguration() {
        return proxyConfiguration;
    }

    @JsonProperty
    public StroomDuration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod;
    }

    @JsonProperty(PROP_NAME_TLS)
    @Nullable
    public HttpTlsConfiguration getTlsConfiguration() {
        return tlsConfiguration;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpClientConfiguration that = (HttpClientConfiguration) o;
        return cookiesEnabled == that.cookiesEnabled &&
               maxConnections == that.maxConnections &&
               maxConnectionsPerRoute == that.maxConnectionsPerRoute &&
               retries == that.retries &&
               Objects.equals(timeout, that.timeout) &&
               Objects.equals(connectionTimeout, that.connectionTimeout) &&
               Objects.equals(connectionRequestTimeout, that.connectionRequestTimeout) &&
               Objects.equals(timeToLive, that.timeToLive) &&
               Objects.equals(keepAlive, that.keepAlive) &&
               Objects.equals(userAgent, that.userAgent) &&
               Objects.equals(proxyConfiguration, that.proxyConfiguration) &&
               Objects.equals(validateAfterInactivityPeriod, that.validateAfterInactivityPeriod) &&
               Objects.equals(tlsConfiguration, that.tlsConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeout,
                connectionTimeout,
                connectionRequestTimeout,
                timeToLive,
                cookiesEnabled,
                maxConnections,
                maxConnectionsPerRoute,
                keepAlive,
                retries,
                userAgent,
                proxyConfiguration,
                validateAfterInactivityPeriod,
                tlsConfiguration);
    }

    @Override
    public String toString() {
        return "HttpClientConfiguration{" +
               "timeout=" + timeout +
               ", connectionTimeout=" + connectionTimeout +
               ", connectionRequestTimeout=" + connectionRequestTimeout +
               ", timeToLive=" + timeToLive +
               ", cookiesEnabled=" + cookiesEnabled +
               ", maxConnections=" + maxConnections +
               ", maxConnectionsPerRoute=" + maxConnectionsPerRoute +
               ", keepAlive=" + keepAlive +
               ", retries=" + retries +
               ", userAgent='" + userAgent + '\'' +
               ", proxyConfiguration=" + proxyConfiguration +
               ", validateAfterInactivityPeriod=" + validateAfterInactivityPeriod +
               ", tlsConfiguration=" + tlsConfiguration +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder(new HttpClientConfiguration());
    }

    public static class Builder extends AbstractBuilder<HttpClientConfiguration, HttpClientConfiguration.Builder> {

        private StroomDuration timeout;
        private StroomDuration connectionTimeout;
        private StroomDuration connectionRequestTimeout;
        private StroomDuration timeToLive;
        private boolean cookiesEnabled;
        private int maxConnections;
        private int maxConnectionsPerRoute;
        private StroomDuration keepAlive;
        private int retries;
        private String userAgent;
        private HttpProxyConfiguration proxyConfiguration;
        private StroomDuration validateAfterInactivityPeriod;
        private HttpTlsConfiguration tlsConfiguration;

        public Builder() {
            this(new HttpClientConfiguration());
        }

        public Builder(final HttpClientConfiguration httpClientConfig) {
            timeout = httpClientConfig.timeout;
            connectionTimeout = httpClientConfig.connectionTimeout;
            connectionRequestTimeout = httpClientConfig.connectionRequestTimeout;
            timeToLive = httpClientConfig.timeToLive;
            cookiesEnabled = httpClientConfig.cookiesEnabled;
            maxConnections = httpClientConfig.maxConnections;
            maxConnectionsPerRoute = httpClientConfig.maxConnectionsPerRoute;
            keepAlive = httpClientConfig.keepAlive;
            retries = httpClientConfig.retries;
            userAgent = httpClientConfig.userAgent;
            validateAfterInactivityPeriod = httpClientConfig.validateAfterInactivityPeriod;
            tlsConfiguration = httpClientConfig.tlsConfiguration;
        }

        public Builder timeout(final StroomDuration timeout) {
            this.timeout = timeout;
            return self();
        }

        public Builder connectionTimeout(final StroomDuration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return self();
        }

        public Builder connectionRequestTimeout(final StroomDuration connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
            return self();
        }

        public Builder timeToLive(final StroomDuration timeToLive) {
            this.timeToLive = timeToLive;
            return self();
        }

        public Builder cookiesEnabled(final boolean cookiesEnabled) {
            this.cookiesEnabled = cookiesEnabled;
            return self();
        }

        public Builder maxConnections(final int maxConnections) {
            this.maxConnections = maxConnections;
            return self();
        }

        public Builder maxConnectionsPerRoute(final int maxConnectionsPerRoute) {
            this.maxConnectionsPerRoute = maxConnectionsPerRoute;
            return self();
        }

        public Builder keepAlive(final StroomDuration keepAlive) {
            this.keepAlive = keepAlive;
            return self();
        }

        public Builder retries(final int retries) {
            this.retries = retries;
            return self();
        }

        public Builder userAgent(final String userAgent) {
            this.userAgent = userAgent;
            return self();
        }

        public Builder proxyConfiguration(final HttpProxyConfiguration proxyConfiguration) {
            this.proxyConfiguration = proxyConfiguration;
            return self();
        }

        public Builder validateAfterInactivityPeriod(final StroomDuration validateAfterInactivityPeriod) {
            this.validateAfterInactivityPeriod = validateAfterInactivityPeriod;
            return self();
        }

        public Builder tlsConfiguration(final HttpTlsConfiguration tlsConfiguration) {
            this.tlsConfiguration = tlsConfiguration;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public HttpClientConfiguration build() {
            return new HttpClientConfiguration(
                    timeout,
                    connectionTimeout,
                    connectionRequestTimeout,
                    timeToLive,
                    cookiesEnabled,
                    maxConnections,
                    maxConnectionsPerRoute,
                    keepAlive,
                    retries,
                    userAgent,
                    proxyConfiguration,
                    validateAfterInactivityPeriod,
                    tlsConfiguration);
        }
    }
}
