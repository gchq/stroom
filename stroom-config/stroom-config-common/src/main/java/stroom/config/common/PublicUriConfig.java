package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class PublicUriConfig extends UriConfig {

    public PublicUriConfig() {
        super("https", null, 443);
    }

    @JsonPropertyDescription("The scheme to use when passing requests to the API gateway, " +
            " i.e. https")
    public String getScheme() {
        return super.getScheme();
    }

    @JsonPropertyDescription("The hostname, FQDN or IP address of the public facing " +
            "Stroom API gateway, i.e. Nginx.")
    public String getHostname() {
        return super.getHostname();
    }

    @JsonPropertyDescription("The port to use when passing requests to the API gateway. " +
            "If no port is supplied then no port will be used in the resulting URL and it will " +
            "be inferred from the scheme.")
    public Integer getPort() {
        return super.getPort();
    }
}
