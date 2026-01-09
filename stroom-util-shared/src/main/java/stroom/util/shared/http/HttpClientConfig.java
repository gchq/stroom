/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.shared.http;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class HttpClientConfig {

    private static final SimpleDuration DEFAULT_TIMEOUT =
            SimpleDuration.builder().time(3).timeUnit(TimeUnit.MINUTES).build();
    private static final SimpleDuration DEFAULT_CONNECTION_TIMEOUT =
            SimpleDuration.builder().time(3).timeUnit(TimeUnit.MINUTES).build();
    private static final SimpleDuration DEFAULT_CONNECTION_REQUEST_TIMEOUT =
            SimpleDuration.builder().time(3).timeUnit(TimeUnit.MINUTES).build();
    private static final SimpleDuration DEFAULT_TIME_TO_LIVE =
            SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
    private static final boolean DEFAULT_COOKIES_ENABLED = false;
    private static final int DEFAULT_MAX_CONNECTIONS = 1_024;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 1_024;
    private static final SimpleDuration DEFAULT_KEEP_ALIVE =
            SimpleDuration.builder().time(0).timeUnit(TimeUnit.MINUTES).build();
    private static final int DEFAULT_RETRIES = 0;
    private static final SimpleDuration DEFAULT_VALIDATE_AFTER_INACTIVITY_PERIOD =
            SimpleDuration.builder().time(0).timeUnit(TimeUnit.MINUTES).build();

    @JsonPropertyDescription("Determines the timeout until arrival of a response from the opposite endpoint. " +
                             "A timeout value of zero is interpreted as an infinite timeout. " +
                             "Default: 3 minutes")
    @JsonProperty
    @NotNull
    private final SimpleDuration timeout;

    @JsonPropertyDescription("Determines the timeout until a new connection is fully established. " +
                             "This may also include transport security negotiation exchanges such as SSL or TLS " +
                             "protocol negotiation. " +
                             "A timeout value of zero is interpreted as an infinite timeout. " +
                             "Default: 3 minutes")
    @JsonProperty
    @NotNull
    private final SimpleDuration connectionTimeout;

    @JsonPropertyDescription("Returns the connection lease request timeout used when requesting a connection from " +
                             "the connection manager. " +
                             "Default: 3 minutes")
    @JsonProperty
    @NotNull
    private final SimpleDuration connectionRequestTimeout;

    @JsonPropertyDescription("The maximum time a pooled connection can stay idle (not leased to any thread) " +
                             "before it is shut down. " +
                             "Default: 1 hour")
    @JsonProperty
    @NotNull
    private final SimpleDuration timeToLive;

    @JsonProperty
    private final boolean cookiesEnabled;

    @JsonProperty
    @Min(1)
    @Max(Integer.MAX_VALUE)
    private final int maxConnections;

    @JsonProperty
    @Min(1)
    @Max(Integer.MAX_VALUE)
    private final int maxConnectionsPerRoute;

    @JsonPropertyDescription("The maximum time a connection will be kept alive before it is reconnected. " +
                             "If set to 0, connections will be immediately closed after every request/response. " +
                             "Default: 0")
    @JsonProperty
    @NotNull
    private final SimpleDuration keepAlive;

    @JsonProperty
    @Min(0)
    @Max(1000)
    private final int retries;

    // Changed this to be a string rather than an optional to avoid serialisation issues when
    // we merge our config.yml node tree with a default node tree and then serialise for drop wiz to
    // read.
    @JsonProperty
    private final String userAgent;

    @JsonProperty
    private final HttpProxyConfig proxy;

    @JsonProperty
    @NotNull
    private final SimpleDuration validateAfterInactivityPeriod;

    @JsonProperty
    private final HttpTlsConfig tls;

    @SuppressWarnings("unused")
    @JsonCreator
    public HttpClientConfig(
            @JsonProperty("timeout") final SimpleDuration timeout,
            @JsonProperty("connectionTimeout") final SimpleDuration connectionTimeout,
            @JsonProperty("connectionRequestTimeout") final SimpleDuration connectionRequestTimeout,
            @JsonProperty("timeToLive") final SimpleDuration timeToLive,
            @JsonProperty("cookiesEnabled") final Boolean cookiesEnabled,
            @JsonProperty("maxConnections") final Integer maxConnections,
            @JsonProperty("maxConnectionsPerRoute") final Integer maxConnectionsPerRoute,
            @JsonProperty("keepAlive") final SimpleDuration keepAlive,
            @JsonProperty("retries") final int retries,
            @JsonProperty("userAgent") final String userAgent,
            @JsonProperty("proxy") final HttpProxyConfig proxy,
            @JsonProperty("validateAfterInactivityPeriod") final SimpleDuration validateAfterInactivityPeriod,
            @JsonProperty("tls") final HttpTlsConfig tls) {
        this.timeout = NullSafe.requireNonNullElse(timeout, DEFAULT_TIMEOUT);
        this.connectionTimeout = NullSafe.requireNonNullElse(connectionTimeout, DEFAULT_CONNECTION_TIMEOUT);
        this.connectionRequestTimeout = NullSafe.requireNonNullElse(
                connectionRequestTimeout, DEFAULT_CONNECTION_REQUEST_TIMEOUT);
        this.timeToLive = NullSafe.requireNonNullElse(timeToLive, DEFAULT_TIME_TO_LIVE);
        this.cookiesEnabled = NullSafe.requireNonNullElse(cookiesEnabled, DEFAULT_COOKIES_ENABLED);
        this.maxConnections = NullSafe.requireNonNullElse(maxConnections, DEFAULT_MAX_CONNECTIONS);
        this.maxConnectionsPerRoute = NullSafe.requireNonNullElse(
                maxConnectionsPerRoute, DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
        this.keepAlive = NullSafe.requireNonNullElse(keepAlive, DEFAULT_KEEP_ALIVE);
        this.retries = retries;
        this.userAgent = userAgent;
        this.proxy = proxy;
        this.validateAfterInactivityPeriod = NullSafe.requireNonNullElse(
                validateAfterInactivityPeriod, DEFAULT_VALIDATE_AFTER_INACTIVITY_PERIOD);
        this.tls = NullSafe.requireNonNullElseGet(tls, () -> HttpTlsConfig.builder().build());
    }

    public SimpleDuration getKeepAlive() {
        return keepAlive;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public SimpleDuration getTimeout() {
        return timeout;
    }

    public SimpleDuration getConnectionTimeout() {
        return connectionTimeout;
    }

    public SimpleDuration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public SimpleDuration getTimeToLive() {
        return timeToLive;
    }

    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getRetries() {
        return retries;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public HttpProxyConfig getProxy() {
        return proxy;
    }

    public SimpleDuration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod;
    }


    public HttpTlsConfig getTls() {
        return tls;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpClientConfig that = (HttpClientConfig) o;
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
               Objects.equals(proxy, that.proxy) &&
               Objects.equals(validateAfterInactivityPeriod, that.validateAfterInactivityPeriod) &&
               Objects.equals(tls, that.tls);
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
                proxy,
                validateAfterInactivityPeriod,
                tls);
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
               ", proxyConfig=" + proxy +
               ", validateAfterInactivityPeriod=" + validateAfterInactivityPeriod +
               ", tlsConfiguration=" + tls +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<HttpClientConfig, Builder> {

        private SimpleDuration timeout = DEFAULT_TIMEOUT;
        private SimpleDuration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private SimpleDuration connectionRequestTimeout = DEFAULT_CONNECTION_REQUEST_TIMEOUT;
        private SimpleDuration timeToLive = DEFAULT_TIME_TO_LIVE;
        private boolean cookiesEnabled = DEFAULT_COOKIES_ENABLED;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private int maxConnectionsPerRoute = DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
        private SimpleDuration keepAlive = DEFAULT_KEEP_ALIVE;
        private int retries = DEFAULT_RETRIES;
        private String userAgent;
        private HttpProxyConfig proxyConfig;
        private SimpleDuration validateAfterInactivityPeriod = DEFAULT_VALIDATE_AFTER_INACTIVITY_PERIOD;
        private HttpTlsConfig tlsConfiguration;

        private Builder() {
        }

        private Builder(final HttpClientConfig httpClientConfig) {
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
            proxyConfig = httpClientConfig.proxy;
            validateAfterInactivityPeriod = httpClientConfig.validateAfterInactivityPeriod;
            tlsConfiguration = httpClientConfig.tls;
        }

        public Builder timeout(final SimpleDuration timeout) {
            this.timeout = timeout;
            return self();
        }

        public Builder connectionTimeout(final SimpleDuration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return self();
        }

        public Builder connectionRequestTimeout(final SimpleDuration connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
            return self();
        }

        public Builder timeToLive(final SimpleDuration timeToLive) {
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

        public Builder keepAlive(final SimpleDuration keepAlive) {
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

        public Builder proxyConfig(final HttpProxyConfig proxyConfig) {
            this.proxyConfig = proxyConfig;
            return self();
        }

        public Builder validateAfterInactivityPeriod(final SimpleDuration validateAfterInactivityPeriod) {
            this.validateAfterInactivityPeriod = validateAfterInactivityPeriod;
            return self();
        }

        public Builder tlsConfiguration(final HttpTlsConfig tlsConfiguration) {
            this.tlsConfiguration = tlsConfiguration;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
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
                    proxyConfig,
                    validateAfterInactivityPeriod,
                    tlsConfiguration);
        }
    }
}
