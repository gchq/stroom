/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public final class AuthenticationConfig extends AbstractConfig {

    public static final String PROP_NAME_EMAIL = "email";
    public static final String PROP_NAME_TOKEN = "token";
    public static final String PROP_NAME_SESSION_ID_COOKIE_MAX_AGE = "sessionIdCookieMaxAge";
    public static final String PROP_NAME_USER_SERVICE = "userService";
    public static final String PROP_NAME_PASSWORD_INTEGRITY_CHECKS = "passwordIntegrityChecks";
    public static final String PROP_NAME_OWN_PATH = "ownPath";
    public static final String PROP_NAME_AUTHORISATION_SERVICE = "authorisationService";
    public static final String PROP_NAME_STROOM = "stroom";

    @Nullable
    @JsonProperty(PROP_NAME_EMAIL)
    public EmailConfig emailConfig = new EmailConfig();
    @Valid
    @NotNull
    @JsonProperty
    private String certificateDnPattern = ".*\\((.*)\\)";
    @Valid
    @NotNull
    @JsonProperty
    private int certificateDnCaptureGroupIndex = 1;
    //TODO: change all these URLs so they exclude the actual FQDN. Just have them be the paths.
    @Valid
    @NotNull
    @JsonProperty
    private String loginUrl = "https://localhost/s/login";
    @Valid
    @NotNull
    @JsonProperty
    private String changePasswordUrl = "https://localhost/s/changepassword";
    @Valid
    @NotNull
    @JsonProperty
    private String advertisedHost = "https://localhost";
    @Nullable
    @JsonProperty
    private Integer httpPort;
    @Nullable
    @JsonProperty
    private Integer httpsPort;
    @Nullable
    @JsonProperty
    private Integer failedLoginLockThreshold = 3;
    @Nullable
    @JsonProperty
    private String unauthorisedUrl = "https://localhost/s/unauthorised";
    @Nullable
    @JsonProperty(PROP_NAME_TOKEN)
    private TokenConfig tokenConfig = new TokenConfig();

    @Nullable
    @JsonProperty(PROP_NAME_SESSION_ID_COOKIE_MAX_AGE)
    private int sessionIdCookieMaxAge = 2592000; // 259200 = 1 month

    @NotNull
    @JsonProperty(PROP_NAME_USER_SERVICE)
    private UserServiceConfig userServiceConfig = new UserServiceConfig();

    @NotNull
    @JsonProperty(PROP_NAME_PASSWORD_INTEGRITY_CHECKS)
    private PasswordIntegrityChecksConfig passwordIntegrityChecksConfig = new PasswordIntegrityChecksConfig();

    @NotNull
    @JsonProperty(PROP_NAME_OWN_PATH)
    private String ownPath = "api/authentication";

    @NotNull
    @JsonProperty(PROP_NAME_AUTHORISATION_SERVICE)
    private AuthorisationServiceConfig authorisationServiceConfig = new AuthorisationServiceConfig();

    @Nullable
    @JsonProperty(PROP_NAME_STROOM)
    private StroomConfig stroomConfig = new StroomConfig();

//    public final DataSourceFactory getDataSourceFactory() {
//        return this.dataSourceFactory;
//    }
//
//    public final FlywayFactory getFlywayFactory() {
//        return this.flywayFactory;
//    }
//
//    public final JooqFactory getJooqFactory() {
//        return this.jooqFactory;
//    }


    public final String getCertificateDnPattern() {
        return this.certificateDnPattern;
    }

    public final String getLoginUrl() {
        return this.loginUrl;
    }

    public String getChangePasswordUrl() {
        return changePasswordUrl;
    }

    public final String getAdvertisedHost() {
        return this.advertisedHost;
    }

    @JsonProperty(PROP_NAME_EMAIL)
    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    public void setEmailConfig(EmailConfig emailConfig) {
        this.emailConfig = emailConfig;
    }

    public Integer getFailedLoginLockThreshold() {
        return this.failedLoginLockThreshold;
    }

    @JsonProperty(PROP_NAME_USER_SERVICE)
    public UserServiceConfig getUserServiceConfig() {
        return userServiceConfig;
    }

    public void setUserServiceConfig(UserServiceConfig userServiceConfig) {
        this.userServiceConfig = userServiceConfig;
    }

    @JsonProperty(PROP_NAME_TOKEN)
    public TokenConfig getTokenConfig() {
        return tokenConfig;
    }

    public void setTokenConfig(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
    }

    @JsonProperty(PROP_NAME_SESSION_ID_COOKIE_MAX_AGE)
    public int getSessionIdCookieMaxAge() {
        return sessionIdCookieMaxAge;
    }

    @Nullable
    public String getUnauthorisedUrl() {
        return unauthorisedUrl;
    }

    @JsonProperty(PROP_NAME_PASSWORD_INTEGRITY_CHECKS)
    public PasswordIntegrityChecksConfig getPasswordIntegrityChecksConfig() {
        return passwordIntegrityChecksConfig;
    }

    public void setPasswordIntegrityChecksConfig(PasswordIntegrityChecksConfig passwordIntegrityChecksConfig) {
        this.passwordIntegrityChecksConfig = passwordIntegrityChecksConfig;
    }

    public int getCertificateDnCaptureGroupIndex() {
        return certificateDnCaptureGroupIndex;
    }

    @JsonProperty(PROP_NAME_OWN_PATH)
    public String getOwnPath() {
        return ownPath;
    }

    @JsonProperty(PROP_NAME_AUTHORISATION_SERVICE)
    public AuthorisationServiceConfig getAuthorisationServiceConfig() {
        return authorisationServiceConfig;
    }

    public void setAuthorisationServiceConfig(AuthorisationServiceConfig authorisationServiceConfig) {
        this.authorisationServiceConfig = authorisationServiceConfig;
    }

//    public final Integer getHttpPort() {
//        return getPort();
//    }
//
//    public final Integer getHttpsPort() {
//        return getPort();
//    }

//    private Integer getPort() {
//        DefaultServerFactory serverFactory = (DefaultServerFactory) this.getServerFactory();
//        Integer port = serverFactory.getApplicationConnectors().stream()
//                .filter(connectorFactory -> connectorFactory instanceof HttpConnectorFactory)
//                .map(connectorFactory -> (HttpConnectorFactory) connectorFactory)
//                .map(HttpConnectorFactory::getPort)
//                .findFirst()
//                .get();
//        return port;
//    }

    @JsonProperty(PROP_NAME_STROOM)
    public StroomConfig getStroomConfig() {
        return stroomConfig;
    }

    public void setStroomConfig(StroomConfig stroomConfig) {
        this.stroomConfig = stroomConfig;
    }
}
