package stroom.security.impl;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.AssertTrue;

@Singleton
public class AuthenticationConfig extends AbstractConfig {
    public static final String PROP_NAME_AUTHENTICATION_REQUIRED = "authenticationRequired";
    public static final String PROP_NAME_OPENID = "openId";
    public static final String PROP_NAME_PREVENT_LOGIN = "preventLogin";
    public static final String PROP_NAME_USER_NAME_PATTERN = "userNamePattern";

    private boolean authenticationRequired = true;
    private OpenIdConfig openIdConfig = new OpenIdConfig();
    private boolean preventLogin;

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

    @JsonProperty(PROP_NAME_OPENID)
    public OpenIdConfig getOpenIdConfig() {
        return openIdConfig;
    }

    @SuppressWarnings("unused")
    public void setOpenIdConfig(final OpenIdConfig openIdConfig) {
        this.openIdConfig = openIdConfig;
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

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                ", authenticationRequired=" + authenticationRequired +
                ", preventLogin=" + preventLogin +
                '}';
    }
}
