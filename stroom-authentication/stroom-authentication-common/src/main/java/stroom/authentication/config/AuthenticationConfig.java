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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidationSeverity;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.NotNull;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public final class AuthenticationConfig extends AbstractConfig {

    public static final String PROP_NAME_EMAIL = "email";
    public static final String PROP_NAME_TOKEN = "token";
    public static final String PROP_NAME_OAUTH2 = "oauth2";
    public static final String PROP_NAME_PASSWORD_INTEGRITY_CHECKS = "passwordIntegrityChecks";

    private boolean useDefaultOpenIdCredentials = true;
    private String certificateDnPattern = ".*\\((.*)\\)";
    private int certificateDnCaptureGroupIndex = 1;
    private String loginUrl = "/s/login";
    private String changePasswordUrl = "/s/changepassword";
    private Integer failedLoginLockThreshold = 3;
    private String unauthorisedUrl = "/s/unauthorised";

    private EmailConfig emailConfig = new EmailConfig();
    private TokenConfig tokenConfig = new TokenConfig();
    private OAuth2Config oAuth2Config = new OAuth2Config();
    private PasswordIntegrityChecksConfig passwordIntegrityChecksConfig = new PasswordIntegrityChecksConfig();

    @AssertFalse(
            message = "Using default OpenId authentication credentials. These should only be used " +
                    "in test/demo environments. Set stroom.authentication.useDefaultOpenIdCredentials to false for " +
                    "production environments.",
            payload = ValidationSeverity.Warning.class)
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @ReadOnly
    @JsonProperty()
    @JsonPropertyDescription("If true, stroom will use a set of default authentication credentials to allow" +
            "API calls from stroom-proxy. For test or demonstration purposes only, set to false for production")
    public boolean isUseDefaultOpenIdCredentials() {
        return useDefaultOpenIdCredentials;
    }

    public void setUseDefaultOpenIdCredentials(final boolean useDefaultOpenIdCredentials) {
        this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
    }

    @NotNull
    @JsonProperty
    public final String getCertificateDnPattern() {
        return this.certificateDnPattern;
    }

    public void setCertificateDnPattern(final String certificateDnPattern) {
        this.certificateDnPattern = certificateDnPattern;
    }

    @NotNull
    @JsonProperty
    public final String getLoginUrl() {
        return this.loginUrl;
    }

    public void setLoginUrl(final String loginUrl) {
        this.loginUrl = loginUrl;
    }

    @NotNull
    @JsonProperty
    public String getChangePasswordUrl() {
        return changePasswordUrl;
    }

    public void setChangePasswordUrl(final String changePasswordUrl) {
        this.changePasswordUrl = changePasswordUrl;
    }

    @Nullable
    @JsonProperty(PROP_NAME_EMAIL)
    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    public void setEmailConfig(EmailConfig emailConfig) {
        this.emailConfig = emailConfig;
    }

    @Nullable
    @JsonProperty
    public Integer getFailedLoginLockThreshold() {
        return this.failedLoginLockThreshold;
    }

    public void setFailedLoginLockThreshold(final Integer failedLoginLockThreshold) {
        this.failedLoginLockThreshold = failedLoginLockThreshold;
    }

    @Nullable
    @JsonProperty(PROP_NAME_TOKEN)
    public TokenConfig getTokenConfig() {
        return tokenConfig;
    }

    public void setTokenConfig(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
    }

    @NotNull
    @JsonProperty(PROP_NAME_OAUTH2)
    public OAuth2Config getOAuth2Config() {
        return oAuth2Config;
    }

    public void setOAuth2Config(final OAuth2Config oAuth2Config) {
        this.oAuth2Config = oAuth2Config;
    }

    @Nullable
    @JsonProperty
    public String getUnauthorisedUrl() {
        return unauthorisedUrl;
    }

    public void setUnauthorisedUrl(final String unauthorisedUrl) {
        this.unauthorisedUrl = unauthorisedUrl;
    }

    @NotNull
    @JsonProperty(PROP_NAME_PASSWORD_INTEGRITY_CHECKS)
    public PasswordIntegrityChecksConfig getPasswordIntegrityChecksConfig() {
        return passwordIntegrityChecksConfig;
    }

    public void setPasswordIntegrityChecksConfig(PasswordIntegrityChecksConfig passwordIntegrityChecksConfig) {
        this.passwordIntegrityChecksConfig = passwordIntegrityChecksConfig;
    }

    @NotNull
    @JsonProperty
    public int getCertificateDnCaptureGroupIndex() {
        return certificateDnCaptureGroupIndex;
    }

    public void setCertificateDnCaptureGroupIndex(final int certificateDnCaptureGroupIndex) {
        this.certificateDnCaptureGroupIndex = certificateDnCaptureGroupIndex;
    }
}
