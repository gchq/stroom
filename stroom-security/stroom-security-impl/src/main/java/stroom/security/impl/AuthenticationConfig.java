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
    private String userNamePattern = "^[a-zA-Z0-9_-]{3,}$";

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

    @JsonPropertyDescription("The regex pattern for user names")
    @JsonProperty(PROP_NAME_USER_NAME_PATTERN)
    @ValidRegex
    public String getUserNamePattern() {
        return userNamePattern;
    }

    @SuppressWarnings("unused")
    public void setUserNamePattern(final String userNamePattern) {
        this.userNamePattern = userNamePattern;
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                ", authenticationRequired=" + authenticationRequired +
                ", preventLogin=" + preventLogin +
                ", userNamePattern='" + userNamePattern + '\'' +
                '}';
    }

//    public static class JwtConfig extends AbstractConfig {
//
//        public static final String PROP_NAME_JWT_ISSUER = "jwtIssuer";
//        public static final String PROP_NAME_ENABLE_TOKEN_REVOCATION_CHECK = "enableTokenRevocationCheck";
//
//        private String jwtIssuer = "stroom";
//        private boolean enableTokenRevocationCheck = true;
//
//        @RequiresRestart(RequiresRestart.RestartScope.UI)
//        @JsonPropertyDescription("The issuer to expect when verifying JWTs.")
//        @JsonProperty(PROP_NAME_JWT_ISSUER)
//        @NotNull
//        public String getJwtIssuer() {
//            return jwtIssuer;
//        }
//
//        @SuppressWarnings("unused")
//        public void setJwtIssuer(final String jwtIssuer) {
//            this.jwtIssuer = jwtIssuer;
//        }
//
//        @RequiresRestart(RequiresRestart.RestartScope.UI)
//        @JsonPropertyDescription("Whether or not to enable remote calls to the auth service to check if " +
//                "a token we have has been revoked.")
//        @JsonProperty(PROP_NAME_ENABLE_TOKEN_REVOCATION_CHECK)
//        public boolean isEnableTokenRevocationCheck() {
//            return enableTokenRevocationCheck;
//        }
//
//        @SuppressWarnings("unused")
//        public void setEnableTokenRevocationCheck(final boolean enableTokenRevocationCheck) {
//            this.enableTokenRevocationCheck = enableTokenRevocationCheck;
//        }
//
//        @Override
//        public String toString() {
//            return "JwtConfig{" +
//                    "jwtIssuer='" + jwtIssuer + '\'' +
//                    ", enableTokenRevocationCheck=" + enableTokenRevocationCheck +
//                    '}';
//        }
//    }
}
