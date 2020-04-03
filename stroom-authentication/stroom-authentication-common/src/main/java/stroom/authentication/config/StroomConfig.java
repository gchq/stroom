package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;

import javax.validation.constraints.NotNull;

/**
 * At the moment stroom is the only RP using stroom-authentication-service.
 * <p>
 * Ideally every client would have to register with this OP manually, and then use those values.
 * <p>
 * But seeing as we only have one we will just make the clientSecret and clientId part of the core configuration.
 */
public class StroomConfig extends AbstractConfig {
    @NotNull
    @JsonProperty
    private String clientId = "PZnJr8kHRKqnlJRQThSI";
    @NotNull
    @JsonProperty
    private String clientSecret = "OtzHiAWLj8QWcwO2IxXmqxpzE2pyg0pMKCghR2aU";
    @NotNull
    @JsonProperty
    private String clientHost = "localhost";

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getClientHost() {
        return clientHost;
    }
}
