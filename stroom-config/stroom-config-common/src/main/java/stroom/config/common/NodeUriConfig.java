package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class NodeUriConfig extends UriConfig {

    public NodeUriConfig() {
        super();
    }

    @JsonCreator
    public NodeUriConfig(@JsonProperty("scheme") final String scheme,
                         @JsonProperty("hostname") final String hostname,
                         @JsonProperty("port") final Integer port,
                         @JsonProperty("pathPrefix") final String pathPrefix) {
        super(scheme, hostname, port, pathPrefix);
    }

    @Override
    @JsonPropertyDescription("The scheme, i.e. http or https. If not set, Stroom will attempt to determine this.")
    public String getScheme() {
        return super.getScheme();
    }

    @Override
    @JsonPropertyDescription("The hostname, FQDN or IP address of the node. " +
            "The value must be resolvable by all other nodes in the cluster. This is used for inter-node " +
            "communications. If not set, Stroom will attempt to determine this.")
    public String getHostname() {
        return super.getHostname();
    }

    @Override
    @JsonPropertyDescription("This is the port to use for inter-node communications. " +
            "This is typically the Drop Wizard application port and would typically only be open within " +
            "the Stroom cluster. If not set, Stroom will attempt to determine this.")
    public Integer getPort() {
        return super.getPort();
    }

    @Override
    @JsonPropertyDescription("An optional prefix to the base path. This may be needed when the inter-node " +
            "communication goes via some form of gateway where the paths are mapped to something else.")
    public String getPathPrefix() {
        return super.getPathPrefix();
    }

    public NodeUriConfig withScheme(final String scheme) {
        return new NodeUriConfig(scheme, getHostname(), getPort(), getPathPrefix());
    }

    public NodeUriConfig withHostname(final String hostname) {
        return new NodeUriConfig(getScheme(), hostname, getPort(), getPathPrefix());
    }

    public NodeUriConfig withPort(final int port) {
        return new NodeUriConfig(getScheme(), getHostname(), port, getPathPrefix());
    }

    public NodeUriConfig withPathPrefix(final String pathPrefix) {
        return new NodeUriConfig(getScheme(), getHostname(), getPort(), pathPrefix);
    }
}
