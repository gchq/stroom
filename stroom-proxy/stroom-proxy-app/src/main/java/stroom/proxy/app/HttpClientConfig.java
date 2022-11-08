package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * This class is essentially a copy of {@link io.dropwizard.client.HttpClientConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method.
 * All uses of {@link io.dropwizard.util.Duration} have been replaced with {@link StroomDuration}
 * for consistency with the rest of the config.
 * Values are extracted from this using reflection by {@link RestClientConfigConverter} so it is
 * key that the method names match.
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class HttpClientConfig extends AbstractConfig implements IsProxyConfig {

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

    // Commented this out as we are unlikely to need configuration of a
    // http proxy for doing rest calls to stroom
//    @Valid
//    @Nullable
//    private ProxyConfiguration proxyConfiguration;
//
    @NotNull
    private final StroomDuration validateAfterInactivityPeriod;

    //    @Valid
    @Nullable
    // TODO 03/12/2021 AT: Make final
    private HttpClientTlsConfig tlsConfiguration;


    public HttpClientConfig() {
        timeout = StroomDuration.ofMillis(500);
        connectionTimeout = StroomDuration.ofMillis(500);
        connectionRequestTimeout = StroomDuration.ofMillis(500);
        timeToLive = StroomDuration.ofHours(1);
        cookiesEnabled = false;
        maxConnections = 1024;
        maxConnectionsPerRoute = 1024;
        keepAlive = StroomDuration.ofMillis(0);
        retries = 0;
        userAgent = null;
        validateAfterInactivityPeriod = StroomDuration.ZERO;
        tlsConfiguration = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public HttpClientConfig(
            @JsonProperty("timeout") final StroomDuration timeout,
            @JsonProperty("connectionTimeout") final StroomDuration connectionTimeout,
            @JsonProperty("connectionRequestTimeout") final StroomDuration connectionRequestTimeout,
            @JsonProperty("timeToLive") final StroomDuration timeToLive,
            @JsonProperty("cookiesEnabled") final boolean cookiesEnabled,
            @JsonProperty("maxConnections") final int maxConnections,
            @JsonProperty("maxConnectionsPerRoute") final int maxConnectionsPerRoute,
            @JsonProperty("keepAlive") final StroomDuration keepAlive,
            @JsonProperty("retries") final int retries,
            @JsonProperty("userAgent") final String userAgent,
            @JsonProperty("validateAfterInactivityPeriod") final StroomDuration validateAfterInactivityPeriod,
            @Nullable @JsonProperty("tls") final HttpClientTlsConfig tlsConfiguration) {

        this.timeout = timeout;
        this.connectionTimeout = connectionTimeout;
        this.connectionRequestTimeout = connectionRequestTimeout;
        this.timeToLive = timeToLive;
        this.cookiesEnabled = cookiesEnabled;
        this.maxConnections = maxConnections;
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        this.keepAlive = keepAlive;
        this.retries = retries;
        this.userAgent = userAgent;
        this.validateAfterInactivityPeriod = validateAfterInactivityPeriod;
        this.tlsConfiguration = tlsConfiguration;
    }

    public StroomDuration getKeepAlive() {
        return keepAlive;
    }

    @JsonProperty
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    @JsonProperty
    public StroomDuration getTimeout() {
        return timeout;
    }

    @JsonProperty
    public StroomDuration getConnectionTimeout() {
        return connectionTimeout;
    }

    @JsonProperty
    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    @JsonProperty
    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    @JsonProperty
    public StroomDuration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
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

    // Commented this out as we are unlikely to need configuration of a
    // http proxy for doing rest calls to stroom
//    @JsonProperty("proxy")
//    @Nullable
//    public ProxyConfiguration getProxyConfiguration() {
//        return proxyConfiguration;
//    }
//
//    @JsonProperty("proxy")
//    public void setProxyConfiguration(ProxyConfiguration proxyConfiguration) {
//        this.proxyConfiguration = proxyConfiguration;
//    }

    @JsonProperty
    public StroomDuration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod;
    }

    @JsonProperty("tls")
    @Nullable
    public HttpClientTlsConfig getTlsConfiguration() {
        return tlsConfiguration;
    }

    @Deprecated(forRemoval = true)
    public void setTlsConfiguration(@Nullable final HttpClientTlsConfig tlsConfiguration) {
        this.tlsConfiguration = tlsConfiguration;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public abstract static class Builder<T extends Builder<T>> {

        protected StroomDuration timeout;
        protected StroomDuration connectionTimeout;
        protected StroomDuration connectionRequestTimeout;
        protected StroomDuration timeToLive;
        protected boolean cookiesEnabled;
        protected int maxConnections;
        protected int maxConnectionsPerRoute;
        protected StroomDuration keepAlive;
        protected int retries;
        protected String userAgent;
        protected StroomDuration validateAfterInactivityPeriod;
        protected HttpClientTlsConfig tlsConfiguration;

        public Builder() {
            // get details from no-args ctor
            final HttpClientConfig httpClientConfig = new HttpClientConfig();
            timeout = httpClientConfig.getTimeout();
            connectionTimeout = httpClientConfig.getConnectionTimeout();
            connectionRequestTimeout = httpClientConfig.getConnectionRequestTimeout();
            timeToLive = httpClientConfig.getTimeToLive();
            cookiesEnabled = httpClientConfig.isCookiesEnabled();
            maxConnections = httpClientConfig.getMaxConnections();
            maxConnectionsPerRoute = httpClientConfig.getMaxConnectionsPerRoute();
            keepAlive = httpClientConfig.getKeepAlive();
            retries = httpClientConfig.getRetries();
            userAgent = httpClientConfig.getUserAgent();
            validateAfterInactivityPeriod = httpClientConfig.getValidateAfterInactivityPeriod();
            tlsConfiguration = httpClientConfig.getTlsConfiguration();
        }

        abstract T getThis();

        public T withTimeout(final StroomDuration timeout) {
            this.timeout = timeout;
            return getThis();
        }

        public T withConnectionTimeout(final StroomDuration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return getThis();
        }

        public T withConnectionRequestTimeout(final StroomDuration connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
            return getThis();
        }

        public T withTimeToLive(final StroomDuration timeToLive) {
            this.timeToLive = timeToLive;
            return getThis();
        }

        public T withCookiesEnabled(final boolean cookiesEnabled) {
            this.cookiesEnabled = cookiesEnabled;
            return getThis();
        }

        public T withMaxConnections(final int maxConnections) {
            this.maxConnections = maxConnections;
            return getThis();
        }

        public T withMaxConnectionsPerRoute(final int maxConnectionsPerRoute) {
            this.maxConnectionsPerRoute = maxConnectionsPerRoute;
            return getThis();
        }

        public T withKeepAlive(final StroomDuration keepAlive) {
            this.keepAlive = keepAlive;
            return getThis();
        }

        public T withRetries(final int retries) {
            this.retries = retries;
            return getThis();
        }

        public T withUserAgent(final String userAgent) {
            this.userAgent = userAgent;
            return getThis();
        }

        public T withValidateAfterInactivityPeriod(final StroomDuration validateAfterInactivityPeriod) {
            this.validateAfterInactivityPeriod = validateAfterInactivityPeriod;
            return getThis();
        }

        public T withTlsConfiguration(final HttpClientTlsConfig tlsConfiguration) {
            this.tlsConfiguration = tlsConfiguration;
            return getThis();
        }

        public HttpClientConfig build() {
            return new HttpClientConfig(
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
                    validateAfterInactivityPeriod,
                    tlsConfiguration);
        }
    }
}
