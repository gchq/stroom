package stroom.config.common;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

@NotInjectableConfig
public abstract class UriConfig extends AbstractConfig {

    private static final String PROP_NAME_SCHEME = "scheme";
    private static final String PROP_NAME_HOSTNAME = "hostname";
    private static final String PROP_NAME_PORT = "port";
    private static final String PROP_NAME_PATH_PREFIX = "pathPrefix";

    private final String scheme;
    private final String hostname;
    private final Integer port;
    private final String pathPrefix;

    public UriConfig() {
        scheme = null;
        hostname = null;
        port = null;
        pathPrefix = null;
    }

    @JsonCreator
    public UriConfig(@JsonProperty("scheme") final String scheme,
                     @JsonProperty("hostname") final String hostname,
                     @JsonProperty("port") final Integer port,
                     @JsonProperty("pathPrefix") final String pathPrefix) {
        this.scheme = scheme;
        this.hostname = hostname;
        this.port = port;
        this.pathPrefix = pathPrefix;
    }

    public UriConfig(final String scheme, final String hostname, final Integer port) {
        this(scheme, hostname, port, null);
    }

    public UriConfig(final String scheme, final String hostname) {
        this(scheme, hostname, null, null);
    }

    @Pattern(regexp = "^https?$")
    public String getScheme() {
        return scheme;
    }

    @ReadOnly
    @JsonProperty(PROP_NAME_HOSTNAME)
    public String getHostname() {
        return hostname;
    }

    @Min(0)
    @Max(65535)
    @JsonProperty(PROP_NAME_PORT)
    public Integer getPort() {
        return port;
    }

    @JsonProperty(PROP_NAME_PATH_PREFIX)
    @Pattern(regexp = "/[^/]+")
    @JsonPropertyDescription("Any prefix to be added to the beginning of paths for this UIR. " +
            "This may be needed if there is some form of gateway in front of Stroom that requires different paths.")
    public String getPathPrefix() {
        return pathPrefix;
    }

    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (scheme != null) {
            sb.append(scheme).append("://");
        }

        if (hostname != null) {
            sb.append(hostname);
        }

        if (port != null) {
            sb.append(":").append(port);
        }

        if (pathPrefix != null && !pathPrefix.isBlank()) {
            if (!pathPrefix.startsWith("/")) {
                sb.append("/");
            }
            sb.append(pathPrefix);
        } else {
            sb.append("/");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UriConfig)) {
            return false;
        }
        final UriConfig uriConfig = (UriConfig) o;
        return Objects.equals(scheme, uriConfig.scheme) &&
                Objects.equals(hostname, uriConfig.hostname) &&
                Objects.equals(port, uriConfig.port) &&
                Objects.equals(pathPrefix, uriConfig.pathPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, hostname, port, pathPrefix);
    }
}
