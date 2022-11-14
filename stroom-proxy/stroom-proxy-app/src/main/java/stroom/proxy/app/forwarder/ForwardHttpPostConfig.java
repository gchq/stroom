package stroom.proxy.app.forwarder;

import stroom.util.cert.SSLConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public class ForwardHttpPostConfig extends AbstractConfig implements ForwardConfig, IsProxyConfig {

    private final boolean enabled;
    private final String name;
    private final String userAgent;
    private final String forwardUrl;
    private final StroomDuration forwardTimeout;
    private final StroomDuration forwardDelay;
    private final Integer forwardChunkSize;
    private final SSLConfig sslConfig;

    public ForwardHttpPostConfig() {
        enabled = true;
        name = null;
        userAgent = null;
        forwardUrl = null;
        forwardTimeout = StroomDuration.ofSeconds(30);
        forwardDelay = StroomDuration.ZERO;
        forwardChunkSize = null;
        sslConfig = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpPostConfig(@JsonProperty("enabled") final boolean enabled,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("userAgent") final String userAgent,
                                 @JsonProperty("forwardUrl") final String forwardUrl,
                                 @JsonProperty("forwardTimeout") final StroomDuration forwardTimeout,
                                 @JsonProperty("forwardDelay") final StroomDuration forwardDelay,
                                 @JsonProperty("forwardChunkSize") final Integer forwardChunkSize,
                                 @JsonProperty("sslConfig") final SSLConfig sslConfig) {
        this.enabled = enabled;
        this.name = name;
        this.userAgent = userAgent;
        this.forwardUrl = forwardUrl;
        this.forwardTimeout = forwardTimeout;
        this.forwardDelay = forwardDelay;
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

    @NotNull
    @JsonProperty
    @Override
    public String getName() {
        return name;
    }

    /**
     * The string to use for the User-Agent request property when forwarding data.
     * If a user-agent is not defined a default user-agent will be used instead.
     */
    @JsonProperty
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * The URL's to forward onto. This is pass-through mode if repoDir is not set
     */
    @NotNull
    @JsonProperty
    public String getForwardUrl() {
        return forwardUrl;
    }

    /**
     * Time out when forwarding. A timeout of zero means an infinite timeout. If not a default
     * timeout will be used.
     */
    @JsonProperty
    public StroomDuration getForwardTimeout() {
        return forwardTimeout;
    }

    /**
     * Debug setting to add a delay
     */
    @JsonProperty
    public StroomDuration getForwardDelay() {
        return forwardDelay;
    }

    /**
     * Chunk size to use over http(s) if not set requires buffer to be fully loaded into memory
     */
    @Min(0)
    @JsonProperty
    public Integer getForwardChunkSize() {
        return forwardChunkSize;
    }

    @JsonProperty
    public SSLConfig getSslConfig() {
        return sslConfig;
    }

    public ForwardHttpPostConfig withSslConfig(final SSLConfig sslConfig) {
        return new ForwardHttpPostConfig(
                enabled, name, userAgent, forwardUrl, forwardTimeout, forwardDelay, forwardChunkSize, sslConfig);
    }

    public static ForwardHttpPostConfig withForwardUrl(final String name,
                                                       final String forwardUrl) {
        return new ForwardHttpPostConfig(
                true,
                name,
                null,
                forwardUrl,
                StroomDuration.ofSeconds(30),
                null,
                null,
                null);
    }

    public static Builder builder() {
        return new Builder();
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
                forwardTimeout,
                that.forwardTimeout) && Objects.equals(forwardDelay,
                that.forwardDelay) && Objects.equals(forwardChunkSize,
                that.forwardChunkSize) && Objects.equals(sslConfig, that.sslConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                name,
                userAgent,
                forwardUrl,
                forwardTimeout,
                forwardDelay,
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
                ", forwardTimeout=" + forwardTimeout +
                ", forwardDelay=" + forwardDelay +
                ", forwardChunkSize=" + forwardChunkSize +
                ", sslConfig=" + sslConfig +
                '}';
    }

    public static class Builder {

        private boolean enabled;
        private String name;
        private String userAgent;
        private String forwardUrl;
        private StroomDuration forwardTimeout;
        private StroomDuration forwardDelay;
        private Integer forwardChunkSize;
        private SSLConfig sslConfig;

        public Builder() {
            final ForwardHttpPostConfig forwardHttpPostConfig = new ForwardHttpPostConfig();

            this.enabled = forwardHttpPostConfig.enabled;
            this.name = forwardHttpPostConfig.name;
            this.userAgent = forwardHttpPostConfig.userAgent;
            this.forwardUrl = forwardHttpPostConfig.forwardUrl;
            this.forwardTimeout = forwardHttpPostConfig.forwardTimeout;
            this.forwardDelay = forwardHttpPostConfig.forwardDelay;
            this.forwardChunkSize = forwardHttpPostConfig.forwardChunkSize;
            this.sslConfig = forwardHttpPostConfig.sslConfig;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder userAgent(final String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder forwardUrl(final String forwardUrl) {
            this.forwardUrl = forwardUrl;
            return this;
        }

        public Builder forwardTimeout(final StroomDuration forwardTimeout) {
            this.forwardTimeout = forwardTimeout;
            return this;
        }

        public Builder forwardDelay(final StroomDuration forwardDelay) {
            this.forwardDelay = forwardDelay;
            return this;
        }

        public Builder forwardChunkSize(final Integer forwardChunkSize) {
            this.forwardChunkSize = forwardChunkSize;
            return this;
        }

        public Builder sslConfig(final SSLConfig sslConfig) {
            this.sslConfig = sslConfig;
            return this;
        }

        public ForwardHttpPostConfig build() {
            return new ForwardHttpPostConfig(
                    enabled,
                    name,
                    userAgent,
                    forwardUrl,
                    forwardTimeout,
                    forwardDelay,
                    forwardChunkSize,
                    sslConfig);
        }
    }
}
