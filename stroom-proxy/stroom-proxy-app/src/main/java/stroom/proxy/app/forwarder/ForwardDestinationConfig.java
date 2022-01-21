package stroom.proxy.app.forwarder;

import stroom.util.cert.SSLConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public class ForwardDestinationConfig extends AbstractConfig implements IsProxyConfig {

    private final String forwardUrl;
    private final Integer forwardTimeoutMs;
    private final Integer forwardDelayMs;
    private final Integer forwardChunkSize;
    // TODO 02/12/2021 AT: Make final
    private SSLConfig sslConfig;

    public ForwardDestinationConfig() {
        forwardUrl = null;
        forwardTimeoutMs = 30_000;
        forwardDelayMs = null;
        forwardChunkSize = null;
        sslConfig = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardDestinationConfig(@JsonProperty("forwardUrl") final String forwardUrl,
                                    @JsonProperty("forwardTimeoutMs") final Integer forwardTimeoutMs,
                                    @JsonProperty("forwardDelayMs") final Integer forwardDelayMs,
                                    @JsonProperty("forwardChunkSize") final Integer forwardChunkSize,
                                    @JsonProperty("sslConfig") final SSLConfig sslConfig) {
        this.forwardUrl = forwardUrl;
        this.forwardTimeoutMs = forwardTimeoutMs;
        this.forwardDelayMs = forwardDelayMs;
        this.forwardChunkSize = forwardChunkSize;
        this.sslConfig = sslConfig;
    }

    /**
     * The URL's to forward onto. This is pass-through mode if repoDir is not set
     */
    @JsonProperty
    public String getForwardUrl() {
        return forwardUrl;
    }

    /**
     * Time out when forwarding
     */
    @JsonProperty
    public Integer getForwardTimeoutMs() {
        return forwardTimeoutMs;
    }

    /**
     * Debug setting to add a delay
     */
    @JsonProperty
    public Integer getForwardDelayMs() {
        return forwardDelayMs;
    }

    /**
     * Chunk size to use over http(s) if not set requires buffer to be fully loaded into memory
     */
    @JsonProperty
    public Integer getForwardChunkSize() {
        return forwardChunkSize;
    }

    @JsonProperty
    public SSLConfig getSslConfig() {
        return sslConfig;
    }

    @Deprecated(forRemoval = true)
    public void setSslConfig(final SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
    }

    public ForwardDestinationConfig withForwardUrl(final String forwardUrl) {
        return new ForwardDestinationConfig(forwardUrl, forwardTimeoutMs, forwardDelayMs, forwardChunkSize, sslConfig);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardDestinationConfig that = (ForwardDestinationConfig) o;
        return Objects.equals(forwardUrl, that.forwardUrl) && Objects.equals(forwardTimeoutMs,
                that.forwardTimeoutMs) && Objects.equals(forwardDelayMs,
                that.forwardDelayMs) && Objects.equals(forwardChunkSize,
                that.forwardChunkSize) && Objects.equals(sslConfig, that.sslConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forwardUrl, forwardTimeoutMs, forwardDelayMs, forwardChunkSize, sslConfig);
    }

    @Override
    public String toString() {
        return "ForwardDestinationConfig{" +
                "forwardUrl='" + forwardUrl + '\'' +
                ", forwardTimeoutMs=" + forwardTimeoutMs +
                ", forwardDelayMs=" + forwardDelayMs +
                ", forwardChunkSize=" + forwardChunkSize +
                ", sslConfig=" + sslConfig +
                '}';
    }
}
