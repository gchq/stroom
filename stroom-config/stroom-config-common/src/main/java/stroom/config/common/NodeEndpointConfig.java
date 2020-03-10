package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.hibernate.validator.constraints.NotBlank;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Objects;

public class NodeEndpointConfig extends AbstractConfig {

    private String scheme = "http";
    private String hostname = "localhost";
    private Integer port = 8080;
    private String pathPrefix = null;

    public NodeEndpointConfig() {
    }

    public NodeEndpointConfig(final String scheme, final String hostname, final Integer port) {
        this.scheme = scheme;
        this.hostname = hostname;
        this.port = port;
    }

    public NodeEndpointConfig(final String scheme, final String hostname) {
        this.scheme = scheme;
        this.hostname = hostname;
    }

    @ReadOnly
    @JsonProperty()
    @Pattern(regexp = "https?")
    public String getScheme() {
        return scheme;
    }

    public void setScheme(final String scheme) {
        this.scheme = scheme;
    }

    @NotNull
    @NotBlank
    @ReadOnly
    @JsonProperty()
    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    @Min(0)
    @Max(65535)
    @ReadOnly
    @JsonProperty()
    @JsonPropertyDescription("This is the port to use for inter-node communications. " +
        "This is typically the Drop Wizard application port.")
    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    @ReadOnly
    @JsonProperty()
    @Pattern(regexp = "/[^/]+")
    @JsonPropertyDescription("An optional prefix to the base path. This may be needed when the inter-node communication" +
        "goes via some form of gateway where the paths are mapped to something else.")
    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(final String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    @JsonIgnore
    private StringBuilder buildBasePath() {
        Objects.requireNonNull(hostname,
                "hostname must be configured");

        final StringBuilder stringBuilder = new StringBuilder()
            .append(scheme)
            .append("://")
            .append(hostname);

        if (port != null) {
            stringBuilder
                .append(":")
                .append(port.toString());
        }
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            stringBuilder.append(pathPrefix);
        }
        return stringBuilder;
    }

    @JsonIgnore
    public String getBasePath() {
        return buildBasePath().toString();
    }

    /**
     * Helper method to build a URL on the API Gateway using the supplied
     * path
     * @param path e.g. /users
     */
    @JsonIgnore
    public String buildNodeUrl(final String path) {
        final StringBuilder stringBuilder = buildBasePath();
        if (path != null && !path.isEmpty()) {
            stringBuilder.append(path);
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return "NodeEndpointConfig{" +
                "scheme='" + scheme + '\'' +
                ", hostname='" + hostname + '\'' +
                ", port=" + port +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final NodeEndpointConfig that = (NodeEndpointConfig) o;
        return Objects.equals(scheme, that.scheme) &&
                Objects.equals(hostname, that.hostname) &&
                Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, hostname, port);
    }
}
