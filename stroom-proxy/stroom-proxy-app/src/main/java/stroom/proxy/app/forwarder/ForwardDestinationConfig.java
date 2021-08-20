package stroom.proxy.app.forwarder;

import stroom.util.cert.SSLConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class ForwardDestinationConfig extends AbstractConfig implements IsProxyConfig {

    private String forwardUrl;
    private Integer forwardTimeoutMs = 30000;
    private Integer forwardDelayMs;
    private Integer forwardChunkSize;
    private SSLConfig sslConfig;

    /**
     * The URL's to forward onto. This is pass-through mode if repoDir is not set
     */
    @JsonProperty
    public String getForwardUrl() {
        return forwardUrl;
    }

    @JsonProperty
    public void setForwardUrl(final String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

    /**
     * Time out when forwarding
     */
    @JsonProperty
    public Integer getForwardTimeoutMs() {
        return forwardTimeoutMs;
    }

    @JsonProperty
    public void setForwardTimeoutMs(final Integer forwardTimeoutMs) {
        this.forwardTimeoutMs = forwardTimeoutMs;
    }

    /**
     * Debug setting to add a delay
     */
    @JsonProperty
    public Integer getForwardDelayMs() {
        return forwardDelayMs;
    }

    @JsonProperty
    public void setForwardDelayMs(final Integer forwardDelayMs) {
        this.forwardDelayMs = forwardDelayMs;
    }

    /**
     * Chunk size to use over http(s) if not set requires buffer to be fully loaded into memory
     */
    @JsonProperty
    public Integer getForwardChunkSize() {
        return forwardChunkSize;
    }

    @JsonProperty
    public void setForwardChunkSize(final Integer forwardChunkSize) {
        this.forwardChunkSize = forwardChunkSize;
    }

    @JsonProperty
    public SSLConfig getSslConfig() {
        return sslConfig;
    }

    @JsonProperty
    public void setSslConfig(final SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
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
