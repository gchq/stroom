package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import javax.inject.Singleton;
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
@Singleton
@JsonPropertyOrder(alphabetic = true)
public class RestClientConfig extends HttpClientConfig implements IsProxyConfig {

    @Min(1)
    @Max(16 * 1024)
    private int minThreads = 1;

    @Min(1)
    @Max(16 * 1024)
    private int maxThreads = 128;

    @Min(1)
    @Max(16 * 1024)
    private int workQueueSize = 8;

    private boolean gzipEnabled = true;

    private boolean gzipEnabledForRequests = true;

    private boolean chunkedEncodingEnabled = true;

    @JsonProperty
    public int getMinThreads() {
        return minThreads;
    }

    @JsonProperty
    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    @JsonProperty
    public int getMaxThreads() {
        return maxThreads;
    }

    @JsonProperty
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @JsonProperty
    public boolean isGzipEnabled() {
        return gzipEnabled;
    }

    @JsonProperty
    public void setGzipEnabled(boolean enabled) {
        this.gzipEnabled = enabled;
    }

    @JsonProperty
    public boolean isGzipEnabledForRequests() {
        return gzipEnabledForRequests;
    }

    @JsonProperty
    public void setGzipEnabledForRequests(boolean enabled) {
        this.gzipEnabledForRequests = enabled;
    }

    @JsonProperty
    public boolean isChunkedEncodingEnabled() {
        return chunkedEncodingEnabled;
    }

    @JsonProperty
    public void setChunkedEncodingEnabled(final boolean chunkedEncodingEnabled) {
        this.chunkedEncodingEnabled = chunkedEncodingEnabled;
    }

    @JsonProperty
    public int getWorkQueueSize() {
        return workQueueSize;
    }

    @JsonProperty
    public void setWorkQueueSize(int workQueueSize) {
        this.workQueueSize = workQueueSize;
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
}
