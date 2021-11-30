package stroom.security.impl;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.inject.Singleton;
import javax.validation.constraints.AssertTrue;

public class AuthenticationConfig extends AbstractConfig {

    public static final String PROP_NAME_AUTHENTICATION_REQUIRED = "authenticationRequired";
    public static final String PROP_NAME_OPENID = "openId";
    public static final String PROP_NAME_PREVENT_LOGIN = "preventLogin";
    public static final String PROP_NAME_USER_NAME_PATTERN = "userNamePattern";

    private static final Boolean AUTHENTICATION_REQUIRED_DEFAULT = Boolean.TRUE;
    private static final Boolean PREVENT_LOGIN_DEFAULT = Boolean.FALSE;

    // Use Boolean rather than boolean to avoid default value confusion when there is nothing in the yaml
    // This way we are in full control of the default value.
    private Boolean authenticationRequired = AUTHENTICATION_REQUIRED_DEFAULT;
    private OpenIdConfig openIdConfig = new OpenIdConfig();
    private Boolean preventLogin = PREVENT_LOGIN_DEFAULT;

    @ReadOnly
    @JsonProperty(PROP_NAME_AUTHENTICATION_REQUIRED)
    @JsonPropertyDescription("Choose whether Stroom requires authenticated access. " +
            "Only intended for use in development or testing.")
    @AssertTrue(
            message = "All authentication is disabled. This should only be used in development or test environments.",
            payload = ValidationSeverity.Warning.class)
    public boolean isAuthenticationRequired() {
        return Objects.requireNonNullElse(authenticationRequired, AUTHENTICATION_REQUIRED_DEFAULT);
    }

    public void setAuthenticationRequired(final Boolean authenticationRequired) {
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
        return Objects.requireNonNullElse(preventLogin, PREVENT_LOGIN_DEFAULT);
    }

    @SuppressWarnings("unused")
    public void setPreventLogin(final Boolean preventLogin) {
        this.preventLogin = preventLogin;
        ;
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                ", authenticationRequired=" + authenticationRequired +
                ", preventLogin=" + preventLogin +
                '}';
    }
}
