package stroom.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.security.SecurityConfig.JwtConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AuthenticationConfig {
    private JwtConfig jwtConfig;
    private SecurityConfig securityConfig ;

    public AuthenticationConfig() {
        this.jwtConfig = new JwtConfig();
        this.securityConfig = new SecurityConfig();
    }

    @Inject
    AuthenticationConfig(final JwtConfig jwtConfig,
                         final SecurityConfig securityConfig) {
        this.jwtConfig = jwtConfig;
        this.securityConfig = securityConfig;
    }

    @JsonProperty("jwt")
    public JwtConfig getJwtConfig() {
        return jwtConfig;
    }

    public void setJwtConfig(final JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @JsonProperty("security")
    public SecurityConfig getSecurityConfig() {
        return securityConfig;
    }

    public void setSecurityConfig(final SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }
}
