package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.dropwizard.client.JerseyClientConfiguration;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class AuthenticationConfig implements IsConfig {
    private boolean authenticationRequired = true;
    private boolean verifySsl;
    private String authServicesBaseUrl = "http://auth-service:8099";
    private OpenIdConfig openIdConfig = new OpenIdConfig();
    private boolean preventLogin;
    private String userNamePattern = "^[a-zA-Z0-9_-]{3,}$";
    private JerseyClientConfiguration jerseyClientConfig = new JerseyClientConfiguration();

    private CacheConfig apiTokenCache = new CacheConfig.Builder()
            .maximumSize(10000L)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

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

    @JsonProperty("openId")
    public OpenIdConfig getOpenIdConfig() {
        return openIdConfig;
    }

    public void setOpenIdConfig(final OpenIdConfig openIdConfig) {
        this.openIdConfig = openIdConfig;
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

    public CacheConfig getApiTokenCache() {
        return apiTokenCache;
    }

    public void setApiTokenCache(final CacheConfig apiTokenCache) {
        this.apiTokenCache = apiTokenCache;
    }

    @JsonProperty("jerseyClient")
    public JerseyClientConfiguration getJerseyClientConfiguration() {
        return jerseyClientConfig;
    }

    @JsonProperty("jerseyClient")
    public void setJerseyClientConfiguration(final JerseyClientConfiguration jerseyClientConfig) {
        this.jerseyClientConfig = jerseyClientConfig;
    }
}
