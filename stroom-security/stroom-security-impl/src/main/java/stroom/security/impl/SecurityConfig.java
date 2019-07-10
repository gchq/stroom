package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SecurityConfig implements IsConfig {
    private AuthenticationConfig authenticationConfig;
    private AuthorisationConfig authorisationConfig;

    public SecurityConfig() {
        this.authenticationConfig = new AuthenticationConfig();
        this.authorisationConfig = new AuthorisationConfig();
    }

    @Inject
    SecurityConfig(final AuthenticationConfig authenticationConfig,
                   final AuthorisationConfig authorisationConfig) {
        this.authenticationConfig = authenticationConfig;
        this.authorisationConfig = authorisationConfig;
    }

    @JsonProperty("authentication")
    public AuthenticationConfig getAuthenticationConfig() {
        return authenticationConfig;
    }

    public void setAuthenticationConfig(final AuthenticationConfig authenticationConfig) {
        this.authenticationConfig = authenticationConfig;
    }

    @JsonProperty("authorisation")
    public AuthorisationConfig getAuthorisationConfig() {
        return authorisationConfig;
    }

    public void setAuthorisationConfig(final AuthorisationConfig authorisationConfig) {
        this.authorisationConfig = authorisationConfig;
    }
}
