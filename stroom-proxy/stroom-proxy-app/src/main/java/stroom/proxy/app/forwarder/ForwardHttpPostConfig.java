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
public class ForwardHttpPostConfig extends AbstractConfig implements ForwardConfig, IsProxyConfig {

    private final boolean enabled;
    private final String name;
    private final String userAgent;
    private final String forwardUrl;
    private final Integer forwardTimeoutMs;
    private final Integer forwardDelayMs;
    private final Integer forwardChunkSize;
    private SSLConfig sslConfig; // TODO : MAKE FINAL

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpPostConfig(@JsonProperty("enabled") final boolean enabled,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("userAgent") final String userAgent,
                                 @JsonProperty("forwardUrl") final String forwardUrl,
                                 @JsonProperty("forwardTimeoutMs") final Integer forwardTimeoutMs,
                                 @JsonProperty("forwardDelayMs") final Integer forwardDelayMs,
                                 @JsonProperty("forwardChunkSize") final Integer forwardChunkSize,
                                 @JsonProperty("sslConfig") final SSLConfig sslConfig) {
        this.enabled = enabled;
        this.name = name;
        this.userAgent = userAgent;
        this.forwardUrl = forwardUrl;
        this.forwardTimeoutMs = forwardTimeoutMs;
        this.forwardDelayMs = forwardDelayMs;
        this.forwardChunkSize = forwardChunkSize;
        this.sslConfig = sslConfig;
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty
    @Override
    public String getName() {
        return name;
    }

    /**
     * The string to use for the User-Agent request property when forwarding data.
     */
    @JsonProperty
    public String getUserAgent() {
        return userAgent;
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

    public void setSslConfig(final SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
    }

    public static ForwardHttpPostConfig withForwardUrl(final String name,
                                                final String forwardUrl) {
        return new ForwardHttpPostConfig(
                true,
                name,
                null,
                forwardUrl,
                30_000,
                null,
                null,
                null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardHttpPostConfig that = (ForwardHttpPostConfig) o;
        return enabled == that.enabled && Objects.equals(name, that.name) && Objects.equals(userAgent,
                that.userAgent) && Objects.equals(forwardUrl, that.forwardUrl) && Objects.equals(
                forwardTimeoutMs,
                that.forwardTimeoutMs) && Objects.equals(forwardDelayMs,
                that.forwardDelayMs) && Objects.equals(forwardChunkSize,
                that.forwardChunkSize) && Objects.equals(sslConfig, that.sslConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                name,
                userAgent,
                forwardUrl,
                forwardTimeoutMs,
                forwardDelayMs,
                forwardChunkSize,
                sslConfig);
    }

    @Override
    public String toString() {
        return "ForwardHttpPostConfig{" +
                "enabled=" + enabled +
                ", name='" + name + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", forwardUrl='" + forwardUrl + '\'' +
                ", forwardTimeoutMs=" + forwardTimeoutMs +
                ", forwardDelayMs=" + forwardDelayMs +
                ", forwardChunkSize=" + forwardChunkSize +
                ", sslConfig=" + sslConfig +
                '}';
    }
}
