package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class NodeUriConfig extends UriConfig {
    @JsonPropertyDescription("The scheme, i.e. http or https")
    public String getScheme() {
        return super.getScheme();
    }

    @JsonPropertyDescription("The hostname, FQDN or IP address of the node. " +
            "The value must be resolvable by all other nodes in the cluster.")
    public String getHostname() {
        return super.getHostname();
    }

    @JsonPropertyDescription("This is the port to use for inter-node communications. " +
            "This is typically the Drop Wizard application port.")
    public Integer getPort() {
        return super.getPort();
    }

    @JsonPropertyDescription("An optional prefix to the base path. This may be needed when the inter-node communication" +
            "goes via some form of gateway where the paths are mapped to something else.")
    public String getPathPrefix() {
        return super.getPathPrefix();
    }
}
