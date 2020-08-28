package stroom.config.app;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.AuthorisationConfig;
import stroom.security.impl.ContentSecurityConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class SecurityConfig extends AbstractConfig {
    public static final String PROP_NAME_IDENTITY = "identity";

    private AuthenticationConfig authenticationConfig = new AuthenticationConfig();
    private AuthorisationConfig authorisationConfig = new AuthorisationConfig();
    private ContentSecurityConfig contentSecurityConfig = new ContentSecurityConfig();
    private IdentityConfig identityConfig = new IdentityConfig();

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

    @JsonProperty("webContent")
    public ContentSecurityConfig getContentSecurityConfig() {
        return contentSecurityConfig;
    }

    public void setContentSecurityConfig(final ContentSecurityConfig contentSecurityConfig) {
        this.contentSecurityConfig = contentSecurityConfig;
    }

    @JsonProperty(PROP_NAME_IDENTITY)
    public IdentityConfig getIdentityConfig() {
        return identityConfig;
    }

    @SuppressWarnings("unused")
    public void setIdentityConfig(final IdentityConfig identityConfig) {
        this.identityConfig = identityConfig;
    }

    @Override
    public String toString() {
        return "SecurityConfig{" +
                "authenticationConfig=" + identityConfig +
                ", authorisationConfig=" + authorisationConfig +
                ", contentSecurityConfig=" + contentSecurityConfig +
                ", authenticationConfig=" + identityConfig +
                '}';
    }
}
