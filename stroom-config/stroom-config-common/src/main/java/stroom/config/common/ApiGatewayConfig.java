package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import java.util.Objects;

public class ApiGatewayConfig implements IsConfig {

    private String hostname;
    private String scheme = "https";
    private Integer port;

    @JsonProperty("hostName")
    @JsonPropertyDescription("The hostname, DNS name or IP address of the " +
        "Stroom API gateway, i.e. Nginx.")
    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    @JsonProperty("scheme")
    @JsonPropertyDescription("The scheme to use when passing requests to the API gateway, " +
        " i.e. https")
    public String getScheme() {
        return scheme;
    }

    public void setScheme(final String scheme) {
        this.scheme = scheme;
    }

    @JsonProperty("port")
    @JsonPropertyDescription("The port to use when passing requests to the API gateway. " +
        "If no port is supplied then no port will be used in the resulting URL and it will " +
        "be inferred from the scheme.")
    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    @JsonIgnore
    public String getBasePath() {
        Objects.requireNonNull(hostname,
            "stroom.apiGateway.hostname must be configured");

        final StringBuilder stringBuilder = new StringBuilder()
            .append(scheme)
            .append("://")
            .append(hostname);

        if (port != null) {
            stringBuilder.append(":")
                .append(port.toString());
        }

        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return "ApiGatewayConfig{" +
            "hostname='" + hostname + '\'' +
            ", scheme='" + scheme + '\'' +
            ", port=" + port +
            '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ApiGatewayConfig that = (ApiGatewayConfig) o;
        return Objects.equals(hostname, that.hostname) &&
            Objects.equals(scheme, that.scheme) &&
            Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, scheme, port);
    }
}
