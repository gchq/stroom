package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.ConfigValidationResults;
import stroom.util.shared.IsConfig;
import stroom.util.shared.ValidationSeverity;

import javax.inject.Singleton;
import javax.validation.constraints.AssertTrue;
import java.util.concurrent.TimeUnit;

@Singleton
public class AuthenticationConfig implements IsConfig {

    public static final String PROP_NAME_AUTHENTICATION_REQUIRED = "authenticationRequired";
    public static final String PROP_NAME_VERIFY_SSL = "verifySsl";
    public static final String PROP_NAME_GET_API_TOKEN = "apiToken";
    public static final String PROP_NAME_AUTH_SERVICES_BASE_URL = "authServicesBaseUrl";
    public static final String PROP_NAME_DURATION_TO_WARN_BEFORE_EXPIRY = "durationToWarnBeforeExpiry";
    public static final String PROP_NAME_JWT = "jwt";
    public static final String PROP_NAME_PREVENT_LOGIN = "preventLogin";
    public static final String PROP_NAME_USER_NAME_PATTERN = "userNamePattern";
    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";
    public static final String PROP_NAME_API_TOKEN_CACHE = "apiTokenCache";

    private String authenticationServiceUrl;
    private boolean authenticationRequired = true;
    private boolean verifySsl;
    private String apiToken = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE1Mzg2NDM1NTQsInN1YiI6ImFkbWluIiwiaXNzIjoic3Ryb29tIn0.J8dqtQf9gGXQlKU_rAye46lUKlJR8-vcyrYhOD0Rxoc";
    private String authServicesBaseUrl = "http://auth-service:8099";
    private String durationToWarnBeforeExpiry = "30d";
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

    @SuppressWarnings("unused")
    public void setAuthenticationServiceUrl(final String authenticationServiceUrl) {
        this.authenticationServiceUrl = authenticationServiceUrl;
    }

    @ReadOnly
    @JsonProperty(PROP_NAME_AUTHENTICATION_REQUIRED)
    @JsonPropertyDescription("Choose whether Stroom requires authenticated access. " +
        "Only intended for use in development or testing.")
    @AssertTrue(
        message = "All authentication is disabled. This should only be used in development or test environments.",
        payload = ValidationSeverity.Warning.class)
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    public void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    @ReadOnly
    @JsonPropertyDescription("If using HTTPS should we verify the server certs")
    @JsonProperty(PROP_NAME_VERIFY_SSL)
    public boolean isVerifySsl() {
        return verifySsl;
    }

    @ReadOnly
    @JsonPropertyDescription("If using HTTPS should we verify the server certs")
    @SuppressWarnings("unused")
    public void setVerifySsl(final boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonProperty(PROP_NAME_GET_API_TOKEN)
    @JsonPropertyDescription("The API token Stroom will use to authenticate itself when accessing other services")
    public String getApiToken() {
        return apiToken;
    }

    @SuppressWarnings("unused")
    public void setApiToken(final String apiToken) {
        this.apiToken = apiToken;
    }

    @JsonPropertyDescription("The URL of the auth service")
    @JsonProperty(PROP_NAME_AUTH_SERVICES_BASE_URL)
    public String getAuthServicesBaseUrl() {
        return authServicesBaseUrl;
    }

    @SuppressWarnings("unused")
    public void setAuthServicesBaseUrl(final String authServicesBaseUrl) {
        this.authServicesBaseUrl = authServicesBaseUrl;
    }

    @JsonProperty(PROP_NAME_DURATION_TO_WARN_BEFORE_EXPIRY)
    public String getDurationToWarnBeforeExpiry() {
        return durationToWarnBeforeExpiry;
    }

    @SuppressWarnings("unused")
    public void setDurationToWarnBeforeExpiry(final String durationToWarnBeforeExpiry) {
        this.durationToWarnBeforeExpiry = durationToWarnBeforeExpiry;
    }

    @JsonProperty(PROP_NAME_JWT)
    @SuppressWarnings("unused")
    public JwtConfig getJwtConfig() {
        return jwtConfig;
    }

    @SuppressWarnings("unused")
    public void setJwtConfig(final JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @JsonPropertyDescription("Prevent new logins to the system. This is useful if the system is scheduled to " +
            "have an outage.")
    @JsonProperty(PROP_NAME_PREVENT_LOGIN)
    public boolean isPreventLogin() {
        return preventLogin;
    }

    @SuppressWarnings("unused")
    public void setPreventLogin(final boolean preventLogin) {
        this.preventLogin = preventLogin;
    }

    @JsonPropertyDescription("The regex pattern for user names")
    @JsonProperty(PROP_NAME_USER_NAME_PATTERN)
    public String getUserNamePattern() {
        return userNamePattern;
    }

    @SuppressWarnings("unused")
    public void setUserNamePattern(final String userNamePattern) {
        this.userNamePattern = userNamePattern;
    }

    @JsonProperty(PROP_NAME_CLIENT_ID)
    public String getClientId() {
        return clientId;
    }

    @SuppressWarnings("unused")
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    public String getClientSecret() {
        return clientSecret;
    }

    @SuppressWarnings("unused")
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @JsonProperty(PROP_NAME_API_TOKEN_CACHE)
    public CacheConfig getApiTokenCache() {
        return apiTokenCache;
    }

    @SuppressWarnings("unused")
    public void setApiTokenCache(final CacheConfig apiTokenCache) {
        this.apiTokenCache = apiTokenCache;
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                "authenticationServiceUrl='" + authenticationServiceUrl + '\'' +
                ", authenticationRequired=" + authenticationRequired +
                ", apiToken='" + apiToken + '\'' +
                ", authServicesBaseUrl='" + authServicesBaseUrl + '\'' +
                ", durationToWarnBeforeExpiry='" + durationToWarnBeforeExpiry + '\'' +
                ", preventLogin=" + preventLogin +
                ", userNamePattern='" + userNamePattern + '\'' +
                '}';
    }

    @Override
    public ConfigValidationResults validateConfig() {
        return ConfigValidationResults.builder(this)
            .addWarningWhen(!authenticationRequired, PROP_NAME_AUTHENTICATION_REQUIRED, "All authentication " +
                "is disabled. This should only be used in development or test environments.")
            .addErrorWhenPatternInvalid(userNamePattern, PROP_NAME_USER_NAME_PATTERN)
            .addErrorWhenModelStringDurationInvalid(durationToWarnBeforeExpiry, PROP_NAME_DURATION_TO_WARN_BEFORE_EXPIRY)
            .build();
    }

    public static class JwtConfig implements IsConfig {

        public static final String PROP_NAME_JWT_ISSUER = "jwtIssuer";
        public static final String PROP_NAME_ENABLE_TOKEN_REVOCATION_CHECK = "enableTokenRevocationCheck";

        private String jwtIssuer = "stroom";
        private boolean enableTokenRevocationCheck = true;

        @RequiresRestart(RequiresRestart.RestartScope.UI)
        @JsonPropertyDescription("The issuer to expect when verifying JWTs.")
        @JsonProperty(PROP_NAME_JWT_ISSUER)
        public String getJwtIssuer() {
            return jwtIssuer;
        }

        @SuppressWarnings("unused")
        public void setJwtIssuer(final String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        @RequiresRestart(RequiresRestart.RestartScope.UI)
        @JsonPropertyDescription("Whether or not to enable remote calls to the auth service to check if " +
                "a token we have has been revoked.")
        @JsonProperty(PROP_NAME_ENABLE_TOKEN_REVOCATION_CHECK)
        public boolean isEnableTokenRevocationCheck() {
            return enableTokenRevocationCheck;
        }

        @SuppressWarnings("unused")
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

        @Override
        public ConfigValidationResults validateConfig() {
            return ConfigValidationResults.builder(this)
                .addErrorWhenEmpty(jwtIssuer, PROP_NAME_JWT_ISSUER)
                .build();
        }
    }
}
