package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Objects;

public class ApiGatewayConfig implements IsConfig {

    public static final String PROP_NAME_HOST_NAME = "hostname";
    public static final String PROP_NAME_SCHEME = "scheme";
    public static final String PROP_NAME_PORT = "port";

    private String hostname;
    private String scheme = "https";
    private Integer port;

    @JsonProperty(PROP_NAME_HOST_NAME)
    @JsonPropertyDescription("The hostname, DNS name or IP address of the " +
        "Stroom API gateway, i.e. Nginx.")
    @NotNull
    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    @JsonProperty(PROP_NAME_SCHEME)
    @JsonPropertyDescription("The scheme to use when passing requests to the API gateway, " +
        " i.e. https")
    @Pattern(regexp = "^https?$")
    @NotNull
    public String getScheme() {
        return scheme;
    }

    public void setScheme(final String scheme) {
        this.scheme = scheme;
    }

    @JsonProperty(PROP_NAME_PORT)
    @JsonPropertyDescription("The port to use when passing requests to the API gateway. " +
        "If no port is supplied then no port will be used in the resulting URL and it will " +
        "be inferred from the scheme.")
    @Min(1)
    @Max(65535)
    @NotNull
    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
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
    public String buildApiGatewayUrl(final String path) {
        StringBuilder stringBuilder = buildBasePath();
        if (path != null && !path.isEmpty()) {
            stringBuilder.append(path);
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
}
