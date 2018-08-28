package stroom.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SecurityConfig {
    private AuthenticationConfig authenticationConfig;

    public SecurityConfig() {
        this.authenticationConfig = new AuthenticationConfig();
    }

    @Inject
    SecurityConfig(final AuthenticationConfig authenticationConfig) {
        this.authenticationConfig = authenticationConfig;
    }

    @JsonProperty("authentication")
    public AuthenticationConfig getAuthenticationConfig() {
        return authenticationConfig;
    }

    public void setAuthenticationConfig(final AuthenticationConfig authenticationConfig) {
        this.authenticationConfig = authenticationConfig;
    }
}
