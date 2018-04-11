package stroom.security;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurityConfig {
    private String authenticationServiceUrl;
    private String advertisedStroomUrl;
    private boolean authenticationRequired = true;

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
}
