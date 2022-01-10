package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * This class is essentially a copy of
 * {@link io.dropwizard.client.JerseyClientConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method
 * All uses of {@link io.dropwizard.util.Duration} have been replaced with {@link stroom.util.time.StroomDuration}
 * for consistency with the rest of the config.
 * Conversion between this and {@link io.dropwizard.client.JerseyClientConfiguration} is done using
 * {@link RestClientConfigConverter} so it is key that the method names match.
 */
@JsonPropertyOrder(alphabetic = true)
public class RestClientConfig extends HttpClientConfig implements IsProxyConfig {

    @Min(1)
    @Max(16 * 1024)
    private final int minThreads;

    @Min(1)
    @Max(16 * 1024)
    private final int maxThreads;

    @Min(1)
    @Max(16 * 1024)
    private final int workQueueSize;

    private final boolean gzipEnabled;

    private final boolean gzipEnabledForRequests;

    private final boolean chunkedEncodingEnabled;

    public RestClientConfig() {
        super();
        minThreads = 1;
        maxThreads = 128;
        workQueueSize = 8;
        gzipEnabled = true;
        gzipEnabledForRequests = true;
        chunkedEncodingEnabled = true;
    }

    @SuppressWarnings({"unused", "checkstyle:LineLength"})
    @JsonCreator
    public RestClientConfig(@JsonProperty("timeout") final StroomDuration timeout,
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
                            @Nullable @JsonProperty("tls") final HttpClientTlsConfig tlsConfiguration,
                            @JsonProperty("minThreads") final int minThreads,
                            @JsonProperty("maxThreads") final int maxThreads,
                            @JsonProperty("workQueueSize") final int workQueueSize,
                            @JsonProperty("gzipEnabled") final boolean gzipEnabled,
                            @JsonProperty("gzipEnabledForRequests") final boolean gzipEnabledForRequests,
                            @JsonProperty("chunkedEncodingEnabled") final boolean chunkedEncodingEnabled) {
        super(timeout,
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

        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.workQueueSize = workQueueSize;
        this.gzipEnabled = gzipEnabled;
        this.gzipEnabledForRequests = gzipEnabledForRequests;
        this.chunkedEncodingEnabled = chunkedEncodingEnabled;
    }

    @JsonProperty
    public int getMinThreads() {
        return minThreads;
    }

    @JsonProperty
    public int getMaxThreads() {
        return maxThreads;
    }

    @JsonProperty
    public boolean isGzipEnabled() {
        return gzipEnabled;
    }

    @JsonProperty
    public boolean isGzipEnabledForRequests() {
        return gzipEnabledForRequests;
    }

    @JsonProperty
    public boolean isChunkedEncodingEnabled() {
        return chunkedEncodingEnabled;
    }

    @JsonProperty
    public int getWorkQueueSize() {
        return workQueueSize;
    }

    @JsonIgnore
    @ValidationMethod(message = ".minThreads must be less than or equal to maxThreads")

    public boolean isThreadPoolSizedCorrectly() {
        return minThreads <= maxThreads;
    }

    @JsonIgnore
    @ValidationMethod(message = ".gzipEnabledForRequests requires gzipEnabled set to true")
    public boolean isCompressionConfigurationValid() {
        return !gzipEnabledForRequests || gzipEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends HttpClientConfig.Builder<Builder> {

        private int minThreads;
        private int maxThreads;
        private int workQueueSize;
        private boolean gzipEnabled;
        private boolean gzipEnabledForRequests;
        private boolean chunkedEncodingEnabled;

        public Builder() {
            super();
            // get details from no-args ctor
            final RestClientConfig restClientConfig = new RestClientConfig();
            minThreads = restClientConfig.getMinThreads();
            maxThreads = restClientConfig.getMaxThreads();
            workQueueSize = restClientConfig.getWorkQueueSize();
            gzipEnabled = restClientConfig.isGzipEnabled();
            gzipEnabledForRequests = restClientConfig.isGzipEnabledForRequests();
            chunkedEncodingEnabled = restClientConfig.isChunkedEncodingEnabled();
        }

        @Override
        Builder getThis() {
            return this;
        }

        public Builder withMinThreads(final int minThreads) {
            this.minThreads = minThreads;
            return this;
        }

        public Builder withMaxThreads(final int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        public Builder withWorkQueueSize(final int workQueueSize) {
            this.workQueueSize = workQueueSize;
            return this;
        }

        public Builder withGzipEnabled(final boolean gzipEnabled) {
            this.gzipEnabled = gzipEnabled;
            return this;
        }

        public Builder withGzipEnabledForRequests(final boolean gzipEnabledForRequests) {
            this.gzipEnabledForRequests = gzipEnabledForRequests;
            return this;
        }

        public Builder withChunkedEncodingEnabled(final boolean chunkedEncodingEnabled) {
            this.chunkedEncodingEnabled = chunkedEncodingEnabled;
            return this;
        }

        @Override
        public RestClientConfig build() {
            return new RestClientConfig(
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
                    tlsConfiguration,
                    minThreads,
                    maxThreads,
                    workQueueSize,
                    gzipEnabled,
                    gzipEnabledForRequests,
                    chunkedEncodingEnabled);
        }
    }
}
