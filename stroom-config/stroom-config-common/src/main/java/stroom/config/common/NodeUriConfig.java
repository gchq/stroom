package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.hibernate.validator.constraints.NotBlank;
import stroom.config.common.UriConfig;
import stroom.util.config.annotations.ReadOnly;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class NodeUriConfig extends UriConfig {
    @Pattern(regexp = "^https?$")
    @JsonProperty(PROP_NAME_SCHEME)
    @JsonPropertyDescription("The scheme, i.e. http or https")
    public String getScheme() {
        return super.getScheme();
    }

    @ReadOnly
    @JsonProperty(PROP_NAME_HOSTNAME)
    @JsonPropertyDescription("The hostname, FQDN or IP address of the node. " +
            "The value must be resolvable by all other nodes in the cluster.")
    public String getHostname() {
        return super.getHostname();
    }

    @Min(0)
    @Max(65535)
    @JsonProperty(PROP_NAME_PORT)
    @JsonPropertyDescription("This is the port to use for inter-node communications. " +
            "This is typically the Drop Wizard application port.")
    public Integer getPort() {
        return super.getPort();
    }

    @JsonProperty(PROP_NAME_PATH_PREFIX)
    @Pattern(regexp = "/[^/]+")
    @JsonPropertyDescription("An optional prefix to the base path. This may be needed when the inter-node communication" +
            "goes via some form of gateway where the paths are mapped to something else.")
    public String getPathPrefix() {
        return super.getPathPrefix();
    }
}
