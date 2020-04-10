package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.ReadOnly;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class PublicUriConfig extends UriConfig {
    @Pattern(regexp = "^https?$")
    @JsonProperty(PROP_NAME_SCHEME)
    @JsonPropertyDescription("The scheme to use when passing requests to the API gateway, " +
            " i.e. https")
    public String getScheme() {
        return super.getScheme();
    }

    @ReadOnly
    @JsonProperty(PROP_NAME_HOSTNAME)
    @JsonPropertyDescription("The hostname, DNS name or IP address of the " +
            "Stroom API gateway, i.e. Nginx.")
    public String getHostname() {
        return super.getHostname();
    }

    @Min(0)
    @Max(65535)
    @JsonProperty(PROP_NAME_PORT)
    @JsonPropertyDescription("The port to use when passing requests to the API gateway. " +
            "If no port is supplied then no port will be used in the resulting URL and it will " +
            "be inferred from the scheme.")
    public Integer getPort() {
        return super.getPort();
    }
}
