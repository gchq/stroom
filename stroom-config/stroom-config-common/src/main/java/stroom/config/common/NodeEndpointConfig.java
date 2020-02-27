package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class NodeEndpointConfig extends AbstractConfig {

    private String scheme = "http";
    private String hostname = null;
    private Integer port = 8080;

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

    @JsonProperty("scheme")
    public String getScheme() {
        return scheme;
    }

    public void setScheme(final String scheme) {
        this.scheme = scheme;
    }

    @NotNull
    @ReadOnly
    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    @JsonProperty("port")
    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    @JsonIgnore
    private StringBuilder buildBasePath() {
        Objects.requireNonNull(hostname,
                "stroom.apiGateway.hostname must be configured");

        // TODO could consider building this on any call to the setters and
        //  then holding it to save re-computing all the time
        final StringBuilder stringBuilder = new StringBuilder()
                .append(scheme)
                .append("://")
                .append(hostname);

        if (port != null) {
            stringBuilder.append(":")
                    .append(port.toString());
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
