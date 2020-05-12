package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class UiUriConfig extends UriConfig {

    public UiUriConfig() {
        super("https", null, 443);
    }

    @JsonPropertyDescription("The scheme, i.e. http or https")
    public String getScheme() {
        return super.getScheme();
    }

    @JsonPropertyDescription("This is the hostname/DNS for where the UI is hosted if different to the public facing " +
            "URI of the server, e.g. during development or some other deployments.")
    public String getHostname() {
        return super.getHostname();
    }

    @JsonPropertyDescription("This is the port to use for UI traffic, if different from the public facing URI.")
    public Integer getPort() {
        return super.getPort();
    }

    @JsonPropertyDescription("An optional prefix to the base path. This may be needed when the UI communication" +
            "goes via some form of gateway where the paths are mapped to something else.")
    public String getPathPrefix() {
        return super.getPathPrefix();
    }
}
