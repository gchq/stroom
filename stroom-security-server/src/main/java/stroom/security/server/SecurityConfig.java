package stroom.security.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurityConfig {
    private String authenticationServiceUrl;
    private String advertisedStroomUrl;
    private boolean authenticationRequired = true;
    private String clientId;
    private String clientSecret;

    @JsonProperty
    public String getAuthenticationServiceUrl() {
        return authenticationServiceUrl;
    }

    @JsonProperty
    public void setAuthenticationServiceUrl(final String authenticationServiceUrl) {
        this.authenticationServiceUrl = authenticationServiceUrl;
    }

    @JsonProperty
    public String getAdvertisedStroomUrl() {
        return advertisedStroomUrl;
    }

    @JsonProperty
    public void setAdvertisedStroomUrl(final String advertisedStroomUrl) {
        this.advertisedStroomUrl = advertisedStroomUrl;
    }

    @JsonProperty
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @JsonProperty
    public void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientSecret() {
        return clientSecret;
    }
}
