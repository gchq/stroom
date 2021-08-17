package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.valuehandling.UnwrapValidatedValue;

import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * This class is essentially a copy of {@link io.dropwizard.client.HttpClientConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method.
 * All uses of {@link io.dropwizard.util.Duration} have been replaced with {@link StroomDuration}
 * for consistency with the rest of the config.
 */
public class HttpClientConfig extends AbstractConfig implements IsProxyConfig {

    @NotNull
    private StroomDuration timeout = StroomDuration.ofMillis(500);

    @NotNull
    private StroomDuration connectionTimeout = StroomDuration.ofMillis(500);

    @NotNull
    private StroomDuration connectionRequestTimeout = StroomDuration.ofMillis(500);

    @NotNull
    private StroomDuration timeToLive = StroomDuration.ofHours(1);

    private boolean cookiesEnabled = false;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private int maxConnections = 1024;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private int maxConnectionsPerRoute = 1024;

    @NotNull
    private StroomDuration keepAlive = StroomDuration.ofMillis(0);

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
    private StroomDuration validateAfterInactivityPeriod = StroomDuration.ZERO;

    public StroomDuration getKeepAlive() {
        return keepAlive;
    }

    @Valid
    @Nullable
    private HttpClientTlsConfig tlsConfiguration;

    @JsonProperty
    public void setKeepAlive(StroomDuration keepAlive) {
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
    public void setTimeout(StroomDuration duration) {
        this.timeout = duration;
    }

    @JsonProperty
    public void setConnectionTimeout(StroomDuration duration) {
        this.connectionTimeout = duration;
    }

    @JsonProperty
    public StroomDuration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    @JsonProperty
    public void setConnectionRequestTimeout(StroomDuration connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    @JsonProperty
    public void setTimeToLive(StroomDuration timeToLive) {
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
    public StroomDuration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod;
    }

    @JsonProperty
    public void setValidateAfterInactivityPeriod(StroomDuration validateAfterInactivityPeriod) {
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
