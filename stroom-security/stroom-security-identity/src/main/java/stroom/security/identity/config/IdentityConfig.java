/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.config;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class IdentityConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String PROP_NAME_EMAIL = "email";
    public static final String PROP_NAME_TOKEN = "token";
    public static final String PROP_NAME_OPENID = "openid";
    public static final String PROP_NAME_PASSWORD_POLICY = "passwordPolicy";
    private static final boolean DEFAULT_AUTO_CREATE_ADMIN_ACCOUNT_ON_BOOT = false;
    public static final boolean DEFAULT_ALLOW_CERTIFICATE_AUTHENTICATION = false;

    private final boolean autoCreateAdminAccountOnBoot;
    private final boolean allowCertificateAuthentication;
    private final String certificateCnPattern;
    private final int certificateCnCaptureGroupIndex;
    private final Integer failedLoginLockThreshold;

    private final EmailConfig emailConfig;
    private final TokenConfig tokenConfig;
    private final OpenIdConfig openIdConfig;
    private final PasswordPolicyConfig passwordPolicyConfig;
    private final IdentityDbConfig dbConfig;

    public IdentityConfig() {
        autoCreateAdminAccountOnBoot = DEFAULT_AUTO_CREATE_ADMIN_ACCOUNT_ON_BOOT;
        allowCertificateAuthentication = DEFAULT_ALLOW_CERTIFICATE_AUTHENTICATION;
        certificateCnPattern = ".*\\((.*)\\)";
        certificateCnCaptureGroupIndex = 1;
        failedLoginLockThreshold = 3;

        emailConfig = new EmailConfig();
        tokenConfig = new TokenConfig();
        openIdConfig = new OpenIdConfig();
        passwordPolicyConfig = new PasswordPolicyConfig();
        dbConfig = new IdentityDbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IdentityConfig(@JsonProperty("autoCreateAdminAccountOnBoot") final Boolean autoCreateAdminAccountOnBoot,
                          @JsonProperty("allowCertificateAuthentication") final Boolean allowCertificateAuthentication,
                          @JsonProperty("certificateCnPattern") final String certificateCnPattern,
                          @JsonProperty("certificateCnCaptureGroupIndex") final int certificateCnCaptureGroupIndex,
                          @JsonProperty("failedLoginLockThreshold") final Integer failedLoginLockThreshold,
                          @JsonProperty(PROP_NAME_EMAIL) final EmailConfig emailConfig,
                          @JsonProperty(PROP_NAME_TOKEN) final TokenConfig tokenConfig,
                          @JsonProperty(PROP_NAME_OPENID) final OpenIdConfig openIdConfig,
                          @JsonProperty(PROP_NAME_PASSWORD_POLICY) final PasswordPolicyConfig passwordPolicyConfig,
                          @JsonProperty("db") final IdentityDbConfig dbConfig) {
        this.autoCreateAdminAccountOnBoot = Objects.requireNonNullElse(
                autoCreateAdminAccountOnBoot,
                DEFAULT_AUTO_CREATE_ADMIN_ACCOUNT_ON_BOOT);
        this.allowCertificateAuthentication = Objects.requireNonNullElse(
                allowCertificateAuthentication,
                DEFAULT_ALLOW_CERTIFICATE_AUTHENTICATION);
        this.certificateCnPattern = certificateCnPattern;
        this.certificateCnCaptureGroupIndex = certificateCnCaptureGroupIndex;
        this.failedLoginLockThreshold = failedLoginLockThreshold;
        this.emailConfig = emailConfig;
        this.tokenConfig = tokenConfig;
        this.openIdConfig = openIdConfig;
        this.passwordPolicyConfig = passwordPolicyConfig;
        this.dbConfig = dbConfig;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("If true, then on boot, Stroom will ensure the presence of a local user account " +
                             "called 'admin' and a group called 'Administrators' with the app permission " +
                             "'Administrator'. If the 'admin' user account is created then its password will be " +
                             "set to 'admin'. This feature also requires that the 'identityProviderType' property " +
                             "is set to INTERNAL_IDP or TEST_CREDENTIALS. The default value for this property is " +
                             "false. If false it is possible to create administrator users/groups using the " +
                             "'manage_users' CLI command.")
    public boolean isAutoCreateAdminAccountOnBoot() {
        return autoCreateAdminAccountOnBoot;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription(
            "In order for clients to be able to login with certificates this property must be set " +
            "to true. For security certificate authentication should not be allowed unless the application is " +
            "adequately secured and HTTPS is configured either directly for DropWizard or by an appropriate reverse " +
            "proxy such as NGINX.")
    public boolean isAllowCertificateAuthentication() {
        return allowCertificateAuthentication;
    }

    @NotNull
    @ValidRegex
    @JsonProperty
    @JsonPropertyDescription(
            "The regular expression pattern that represents the Common Name (CN) value in an X509 " +
            "certificate. The pattern should include a capture group for extracting the user identity from the " +
            "CN value. For example the CN may be of the form 'Joe Bloggs [jbloggs]' in which case the pattern " +
            "would be '.*?\\[([^]]*)\\].*'. The only capture group surrounds the user identity part.")
    public final String getCertificateCnPattern() {
        return this.certificateCnPattern;
    }

    @NotNull
    @Min(0)
    @JsonProperty
    @JsonPropertyDescription(
            "Used in conjunction with property certificateCnPattern. This property value is the " +
            "number of the regex capture group that represents the portion of certificate Common Name (CN) value " +
            "that is the user identity. If all of the CN value is the user identity then set this property to " +
            "0 to capture the whole CN value.")
    public int getCertificateCnCaptureGroupIndex() {
        return certificateCnCaptureGroupIndex;
    }

    @Nullable
    @JsonProperty(PROP_NAME_EMAIL)
    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    @Nullable
    @JsonProperty
    @JsonPropertyDescription("If the number of failed logins is greater than or equal to this value then the " +
                             "account  will be locked.")
    @Min(1)
    public Integer getFailedLoginLockThreshold() {
        return this.failedLoginLockThreshold;
    }

    @NotNull
    @JsonProperty(PROP_NAME_TOKEN)
    public TokenConfig getTokenConfig() {
        return tokenConfig;
    }

    @NotNull
    @JsonProperty(PROP_NAME_OPENID)
    public OpenIdConfig getOpenIdConfig() {
        return openIdConfig;
    }

    @NotNull
    @JsonProperty(PROP_NAME_PASSWORD_POLICY)
    public PasswordPolicyConfig getPasswordPolicyConfig() {
        return passwordPolicyConfig;
    }

    @Override
    @JsonProperty("db")
    public IdentityDbConfig getDbConfig() {
        return dbConfig;
    }

    @Override
    public String toString() {
        return "IdentityConfig{" +
               "autoCreateAdminAccountOnBoot=" + autoCreateAdminAccountOnBoot +
               ", allowCertificateAuthentication=" + allowCertificateAuthentication +
               ", certificateCnPattern='" + certificateCnPattern + '\'' +
               ", certificateCnCaptureGroupIndex=" + certificateCnCaptureGroupIndex +
               ", failedLoginLockThreshold=" + failedLoginLockThreshold +
               ", emailConfig=" + emailConfig +
               ", tokenConfig=" + tokenConfig +
               ", openIdConfig=" + openIdConfig +
               ", passwordPolicyConfig=" + passwordPolicyConfig +
               ", dbConfig=" + dbConfig +
               '}';
    }


    // --------------------------------------------------------------------------------


    @BootStrapConfig
    public static class IdentityDbConfig extends AbstractDbConfig {

        public IdentityDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public IdentityDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
