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
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public final class AuthenticationConfig extends AbstractConfig {
    public static final String PROP_NAME_EMAIL = "email";
    public static final String PROP_NAME_TOKEN = "token";
    public static final String PROP_NAME_OAUTH2 = "oauth2";
    public static final String PROP_NAME_PASSWORD_INTEGRITY_CHECKS = "passwordIntegrityChecks";

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
    @Valid
    @NotNull
    @JsonProperty
    private String loginUrl = "/s/login";
    @Valid
    @NotNull
    @JsonProperty
    private String changePasswordUrl = "/s/changepassword";
    @Nullable
    @JsonProperty
    private Integer failedLoginLockThreshold = 3;
    @Nullable
    @JsonProperty
    private String unauthorisedUrl = "/s/unauthorised";
    @Nullable
    @JsonProperty(PROP_NAME_TOKEN)
    private TokenConfig tokenConfig = new TokenConfig();
    @NotNull
    @JsonProperty(PROP_NAME_OAUTH2)
    private OAuth2Config oAuth2Config = new OAuth2Config();

    @NotNull
    @JsonProperty(PROP_NAME_PASSWORD_INTEGRITY_CHECKS)
    private PasswordIntegrityChecksConfig passwordIntegrityChecksConfig = new PasswordIntegrityChecksConfig();

    public final String getCertificateDnPattern() {
        return this.certificateDnPattern;
    }

    public final String getLoginUrl() {
        return this.loginUrl;
    }

    public String getChangePasswordUrl() {
        return changePasswordUrl;
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

    @JsonProperty(PROP_NAME_TOKEN)
    public TokenConfig getTokenConfig() {
        return tokenConfig;
    }

    public void setTokenConfig(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
    }

    @JsonProperty(PROP_NAME_OAUTH2)
    public OAuth2Config getOAuth2Config() {
        return oAuth2Config;
    }

    public void setOAuth2Config(final OAuth2Config oAuth2Config) {
        this.oAuth2Config = oAuth2Config;
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
}
