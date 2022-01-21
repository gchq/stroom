package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class PublicUriConfig extends UriConfig {

    public PublicUriConfig() {
        super("https", null, 443, null);
    }

    @JsonCreator
    public PublicUriConfig(@JsonProperty("scheme") final String scheme,
                           @JsonProperty("hostname") final String hostname,
                           @JsonProperty("port") final Integer port,
                           @JsonProperty("pathPrefix") final String pathPrefix) {
        super(scheme, hostname, port, pathPrefix);
    }

    @Override
    @JsonPropertyDescription("The scheme to use when passing requests to the API gateway, " +
            " i.e. https")
    public String getScheme() {
        return super.getScheme();
    }

    @Override
    @JsonPropertyDescription("The hostname, FQDN or IP address of the public facing " +
            "Stroom API gateway, i.e. Nginx.")
    public String getHostname() {
        return super.getHostname();
    }

    @Override
    @JsonPropertyDescription("The port to use when passing requests to the API gateway. " +
            "If no port is supplied then no port will be used in the resulting URL and it will " +
            "be inferred from the scheme.")
    public Integer getPort() {
        return super.getPort();
    }
}
