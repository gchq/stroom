package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SecurityConfig implements IsConfig, HasDbConfig {
    private DbConfig dbConfig;
    private AuthenticationConfig authenticationConfig;
    private AuthorisationConfig authorisationConfig;
    private ContentSecurityConfig contentSecurityConfig;

    public SecurityConfig() {
        this.authenticationConfig = new AuthenticationConfig();
        this.authorisationConfig = new AuthorisationConfig();
        this.dbConfig = new DbConfig();
        this.contentSecurityConfig = new ContentSecurityConfig();
    }

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Inject
    SecurityConfig(final AuthenticationConfig authenticationConfig,
                   final AuthorisationConfig authorisationConfig) {
        this.authenticationConfig = authenticationConfig;
        this.authorisationConfig = authorisationConfig;
        this.dbConfig = new DbConfig();
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

    @JsonProperty("webContent")
    public ContentSecurityConfig getContentSecurityConfig() {
        return contentSecurityConfig;
    }

    public void setContentSecurityConfig(final ContentSecurityConfig contentSecurityConfig) {
        this.contentSecurityConfig = contentSecurityConfig;
    }

    @Override
    public String toString() {
        return "SecurityConfig{" +
                "dbConfig=" + dbConfig +
                ", authenticationConfig=" + authenticationConfig +
                ", authorisationConfig=" + authorisationConfig +
                ", webContent=" + contentSecurityConfig +
                '}';
    }
}
