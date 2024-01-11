package stroom.security.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.ValidationSeverity;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class AuthenticationConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_AUTHENTICATION_REQUIRED = "authenticationRequired";
    public static final String PROP_NAME_OPENID = "openId";
    public static final String PROP_NAME_PREVENT_LOGIN = "preventLogin";
    public static final String PROP_NAME_API_KEY_CACHE = "apiKeyCache";
    public static final String PROP_NAME_MAX_API_KEY_EXPIRY_AGE = "maxApiKeyExpiryAge";

    private final CacheConfig apiKeyCache;
    private final StroomDuration maxApiKeyExpiryAge;
    private final boolean authenticationRequired;
    private final StroomOpenIdConfig openIdConfig;
    private final boolean preventLogin;

    public AuthenticationConfig() {
        apiKeyCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterWrite(StroomDuration.ofSeconds(20))
                .build();
        maxApiKeyExpiryAge = StroomDuration.ofDays(365);
        authenticationRequired = true;
        openIdConfig = new StroomOpenIdConfig();
        preventLogin = false;
    }

    @JsonCreator
    public AuthenticationConfig(
            @JsonProperty(PROP_NAME_API_KEY_CACHE) final CacheConfig apiKeyCache,
            @JsonProperty(PROP_NAME_MAX_API_KEY_EXPIRY_AGE) final StroomDuration maxApiKeyExpiryAge,
            @JsonProperty(PROP_NAME_AUTHENTICATION_REQUIRED) final boolean authenticationRequired,
            @JsonProperty(PROP_NAME_OPENID) final StroomOpenIdConfig openIdConfig,
            @JsonProperty(PROP_NAME_PREVENT_LOGIN) final boolean preventLogin) {

        this.apiKeyCache = apiKeyCache;
        this.maxApiKeyExpiryAge = maxApiKeyExpiryAge;
        this.authenticationRequired = authenticationRequired;
        this.openIdConfig = openIdConfig;
        this.preventLogin = preventLogin;
    }

    @JsonProperty(PROP_NAME_API_KEY_CACHE)
    public CacheConfig getApiKeyCache() {
        return apiKeyCache;
    }

    @JsonProperty(PROP_NAME_MAX_API_KEY_EXPIRY_AGE)
    @JsonPropertyDescription("The maximum expiry age for new API keys. Defaults to 365 days.")
    @NotNull
    public StroomDuration getMaxApiKeyExpiryAge() {
        return maxApiKeyExpiryAge;
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

    @JsonProperty(PROP_NAME_OPENID)
    public StroomOpenIdConfig getOpenIdConfig() {
        return openIdConfig;
    }

    @JsonPropertyDescription("Prevent new logins to the system. This is useful if the system is scheduled to " +
            "have an outage.")
    @JsonProperty(PROP_NAME_PREVENT_LOGIN)
    public boolean isPreventLogin() {
        return preventLogin;
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                "apiKeyCache=" + apiKeyCache +
                ", maxApiKeyExpiryAge=" + maxApiKeyExpiryAge +
                ", authenticationRequired=" + authenticationRequired +
                ", openIdConfig=" + openIdConfig +
                ", preventLogin=" + preventLogin +
                '}';
    }
}
