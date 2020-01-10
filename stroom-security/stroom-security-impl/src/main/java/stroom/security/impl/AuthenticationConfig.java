package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class AuthenticationConfig implements IsConfig {
    private String authenticationServiceUrl;
    private boolean authenticationRequired = true;
    private boolean verifySsl;
    private String authServicesBaseUrl = "http://auth-service:8099";
    private JwtConfig jwtConfig = new JwtConfig();
    private boolean preventLogin;
    private String userNamePattern = "^[a-zA-Z0-9_-]{3,}$";
    private String clientId;
    private String clientSecret;

    private CacheConfig apiTokenCache = new CacheConfig.Builder()
            .maximumSize(10000L)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    @JsonPropertyDescription("The URL of the authentication service")
    public String getAuthenticationServiceUrl() {
        return authenticationServiceUrl;
    }

    public void setAuthenticationServiceUrl(final String authenticationServiceUrl) {
        this.authenticationServiceUrl = authenticationServiceUrl;
    }

    @ReadOnly
    @JsonPropertyDescription("Choose whether Stroom requires authenticated access")
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    public void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    @ReadOnly
    @JsonPropertyDescription("If using HTTPS should we verify the server certs")
    public boolean isVerifySsl() {
        return verifySsl;
    }

    @ReadOnly
    @JsonPropertyDescription("If using HTTPS should we verify the server certs")
    public void setVerifySsl(final boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    @JsonPropertyDescription("The URL of the auth service")
    public String getAuthServicesBaseUrl() {
        return authServicesBaseUrl;
    }

    public void setAuthServicesBaseUrl(final String authServicesBaseUrl) {
        this.authServicesBaseUrl = authServicesBaseUrl;
    }

    @JsonProperty("jwt")
    public JwtConfig getJwtConfig() {
        return jwtConfig;
    }

    public  void setJwtConfig(final JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @JsonPropertyDescription("Prevent new logins to the system. This is useful if the system is scheduled to " +
            "have an outage.")
    public boolean isPreventLogin() {
        return preventLogin;
    }

    public void setPreventLogin(final boolean preventLogin) {
        this.preventLogin = preventLogin;
    }

    @JsonPropertyDescription("The regex pattern for user names")
    public String getUserNamePattern() {
        return userNamePattern;
    }

    public void setUserNamePattern(final String userNamePattern) {
        this.userNamePattern = userNamePattern;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public CacheConfig getApiTokenCache() {
        return apiTokenCache;
    }

    public void setApiTokenCache(final CacheConfig apiTokenCache) {
        this.apiTokenCache = apiTokenCache;
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                "authenticationServiceUrl='" + authenticationServiceUrl + '\'' +
                ", authenticationRequired=" + authenticationRequired +
                ", authServicesBaseUrl='" + authServicesBaseUrl + '\'' +
                ", preventLogin=" + preventLogin +
                ", userNamePattern='" + userNamePattern + '\'' +
                '}';
    }

    public static class JwtConfig implements IsConfig {
        private String jwtIssuer= "stroom";
        private boolean enableTokenRevocationCheck = true;

        @RequiresRestart(RequiresRestart.RestartScope.UI)
        @JsonPropertyDescription("The issuer to expect when verifying JWTs.")
        public String getJwtIssuer() {
            return jwtIssuer;
        }

        public void setJwtIssuer(final String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        @RequiresRestart(RequiresRestart.RestartScope.UI)
        @JsonPropertyDescription("Whether or not to enable remote calls to the auth service to check if " +
                "a token we have has been revoked.")
        public boolean isEnableTokenRevocationCheck() {
            return enableTokenRevocationCheck;
        }

        public void setEnableTokenRevocationCheck(final boolean enableTokenRevocationCheck) {
            this.enableTokenRevocationCheck = enableTokenRevocationCheck;
        }

        @Override
        public String toString() {
            return "JwtConfig{" +
                    "jwtIssuer='" + jwtIssuer + '\'' +
                    ", enableTokenRevocationCheck=" + enableTokenRevocationCheck +
                    '}';
        }
    }
}
