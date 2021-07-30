package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import org.hibernate.validator.valuehandling.UnwrapValidatedValue;

import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * This class is essentially a copy of {@link io.dropwizard.client.HttpClientConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method
 */
public class HttpClientConfig extends AbstractConfig implements IsProxyConfig {
    @NotNull
    private Duration timeout = Duration.milliseconds(500);

    @NotNull
    private Duration connectionTimeout = Duration.milliseconds(500);

    @NotNull
    private Duration connectionRequestTimeout = Duration.milliseconds(500);

    @NotNull
    private Duration timeToLive = Duration.hours(1);

    private boolean cookiesEnabled = false;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private int maxConnections = 1024;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private int maxConnectionsPerRoute = 1024;

    @NotNull
    private Duration keepAlive = Duration.milliseconds(0);

    @Min(0)
    @Max(1000)
    private int retries = 0;

    @NotNull
    @UnwrapValidatedValue(false)
    private Optional<String> userAgent = Optional.empty();

    // Commented this out as we are unlikely to need configuration of a
    // http proxy for doing rest calls to stroom
//    @Valid
//    @Nullable
//    private ProxyConfiguration proxyConfiguration;

    @NotNull
    private Duration validateAfterInactivityPeriod = Duration.microseconds(0);

    public Duration getKeepAlive() {
        return keepAlive;
    }

    @Valid
    @Nullable
    private HttpClientTlsConfig tlsConfiguration;

    @JsonProperty
    public void setKeepAlive(Duration keepAlive) {
        this.keepAlive = keepAlive;
    }

    @JsonProperty
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    @JsonProperty
    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    @JsonProperty
    public Duration getTimeout() {
        return timeout;
    }

    @JsonProperty
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    @JsonProperty
    public Duration getTimeToLive() {
        return timeToLive;
    }

    @JsonProperty
    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    @JsonProperty
    public void setTimeout(Duration duration) {
        this.timeout = duration;
    }

    @JsonProperty
    public void setConnectionTimeout(Duration duration) {
        this.connectionTimeout = duration;
    }

    @JsonProperty
    public Duration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    @JsonProperty
    public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    @JsonProperty
    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    @JsonProperty
    public void setCookiesEnabled(boolean enabled) {
        this.cookiesEnabled = enabled;
    }

    @JsonProperty
    public int getMaxConnections() {
        return maxConnections;
    }

    @JsonProperty
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @JsonProperty
    public int getRetries() {
        return retries;
    }

    @JsonProperty
    public void setRetries(int retries) {
        this.retries = retries;
    }

    @JsonProperty
    public Optional<String> getUserAgent() {
        return userAgent;
    }

    @JsonProperty
    public void setUserAgent(Optional<String> userAgent) {
        this.userAgent = userAgent;
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
    public Duration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod;
    }

    @JsonProperty
    public void setValidateAfterInactivityPeriod(Duration validateAfterInactivityPeriod) {
        this.validateAfterInactivityPeriod = validateAfterInactivityPeriod;
    }

    @JsonProperty("tls")
    @Nullable
    public HttpClientTlsConfig getTlsConfiguration() {
        return tlsConfiguration;
    }

    @JsonProperty("tls")
    public void setTlsConfiguration(HttpClientTlsConfig tlsConfiguration) {
        this.tlsConfiguration = tlsConfiguration;
    }
}
