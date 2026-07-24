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
import stroom.util.time.StroomDuration;

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
    private static final int DEFAULT_CERTIFICATE_CN_CAPTURE_GROUP_INDEX = 1;
    private static final boolean DEFAULT_REACTIVATE_INACTIVE_ACCOUNTS_ON_LOGIN = false;
    private static final boolean DEFAULT_ALLOW_LOCKED_ACCOUNT_PASSWORD_RESET = false;
    private static final StroomDuration DEFAULT_PASSWORD_RESET_REQUEST_COOLDOWN = StroomDuration.ofMinutes(1);
    private static final StroomDuration DEFAULT_FAILED_LOGIN_LOCK_DURATION = StroomDuration.ofMinutes(30);

    private final boolean autoCreateAdminAccountOnBoot;
    private final boolean allowCertificateAuthentication;
    private final String certificateCnPattern;
    private final int certificateCnCaptureGroupIndex;
    private final Integer failedLoginLockThreshold;
    private final StroomDuration failedLoginLockDuration;
    private final boolean reactivateInactiveAccountsOnLogin;
    private final boolean allowLockedAccountPasswordReset;
    private final StroomDuration passwordResetRequestCooldown;

    private final EmailConfig emailConfig;
    private final TokenConfig tokenConfig;
    private final OpenIdConfig openIdConfig;
    private final PasswordPolicyConfig passwordPolicyConfig;
    private final IdentityDbConfig dbConfig;

    public IdentityConfig() {
        autoCreateAdminAccountOnBoot = DEFAULT_AUTO_CREATE_ADMIN_ACCOUNT_ON_BOOT;
        allowCertificateAuthentication = DEFAULT_ALLOW_CERTIFICATE_AUTHENTICATION;
        certificateCnPattern = ".*\\((.*)\\)";
        certificateCnCaptureGroupIndex = DEFAULT_CERTIFICATE_CN_CAPTURE_GROUP_INDEX;
        failedLoginLockThreshold = 3;
        failedLoginLockDuration = DEFAULT_FAILED_LOGIN_LOCK_DURATION;
        reactivateInactiveAccountsOnLogin = DEFAULT_REACTIVATE_INACTIVE_ACCOUNTS_ON_LOGIN;
        allowLockedAccountPasswordReset = DEFAULT_ALLOW_LOCKED_ACCOUNT_PASSWORD_RESET;
        passwordResetRequestCooldown = DEFAULT_PASSWORD_RESET_REQUEST_COOLDOWN;

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
                          @JsonProperty("certificateCnCaptureGroupIndex") final Integer certificateCnCaptureGroupIndex,
                          @JsonProperty("failedLoginLockThreshold") final Integer failedLoginLockThreshold,
                          @JsonProperty("failedLoginLockDuration") final StroomDuration failedLoginLockDuration,
                          @JsonProperty("reactivateInactiveAccountsOnLogin") final
                          Boolean reactivateInactiveAccountsOnLogin,
                          @JsonProperty("allowLockedAccountPasswordReset") final
                          Boolean allowLockedAccountPasswordReset,
                          @JsonProperty("passwordResetRequestCooldown") final
                          StroomDuration passwordResetRequestCooldown,
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
        this.certificateCnCaptureGroupIndex = Objects.requireNonNullElse(
                certificateCnCaptureGroupIndex,
                DEFAULT_CERTIFICATE_CN_CAPTURE_GROUP_INDEX);
        this.failedLoginLockThreshold = failedLoginLockThreshold;
        this.failedLoginLockDuration = Objects.requireNonNullElse(
                failedLoginLockDuration,
                DEFAULT_FAILED_LOGIN_LOCK_DURATION);
        this.reactivateInactiveAccountsOnLogin = Objects.requireNonNullElse(
                reactivateInactiveAccountsOnLogin,
                DEFAULT_REACTIVATE_INACTIVE_ACCOUNTS_ON_LOGIN);
        this.allowLockedAccountPasswordReset = Objects.requireNonNullElse(
                allowLockedAccountPasswordReset,
                DEFAULT_ALLOW_LOCKED_ACCOUNT_PASSWORD_RESET);
        this.passwordResetRequestCooldown = Objects.requireNonNullElse(
                passwordResetRequestCooldown,
                DEFAULT_PASSWORD_RESET_REQUEST_COOLDOWN);
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
                             "is set to INTERNAL_IDP. The default value for this property is " +
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
    @JsonProperty
    @JsonPropertyDescription("How long an account locked by reaching 'failedLoginLockThreshold' failed logins " +
                             "stays locked before it is unlocked automatically, so that a lockout is temporary " +
                             "and cannot be used to deny a user access indefinitely. Set to zero (e.g. 'PT0S') to " +
                             "disable automatic unlocking, in which case a locked account stays locked until an " +
                             "administrator unlocks it or the user completes the 'Forgot password' flow. A lock " +
                             "set manually by an administrator is never affected by this and never expires. Only " +
                             "applies when using the internal identity provider.")
    public StroomDuration getFailedLoginLockDuration() {
        return this.failedLoginLockDuration;
    }

    @JsonProperty
    @JsonPropertyDescription("If true, an account that has been marked as inactive by the " +
                             "'Account Maintenance' job will be automatically made active again when the user " +
                             "next authenticates successfully with the correct password. Reactivation only " +
                             "happens on a successful authentication, so resetting a password does not by " +
                             "itself make an inactive account active again. Accounts that are locked, disabled " +
                             "or are processing accounts are never reactivated this way. Only applies when " +
                             "using the internal identity provider. The default value for this property is false.")
    public boolean isReactivateInactiveAccountsOnLogin() {
        return reactivateInactiveAccountsOnLogin;
    }

    @JsonProperty
    @JsonPropertyDescription("If true, a user whose account has been locked by exceeding " +
                             "'failedLoginLockThreshold' may unlock it themselves by completing the " +
                             "'Forgot password' flow. Successfully setting a new password clears the locked " +
                             "state so the user can sign in again. This does not weaken the protection that " +
                             "locking gives against password guessing because completing a reset requires " +
                             "possession of the short lived token that is sent to the account's email " +
                             "address. Requires 'passwordPolicy.allowPasswordResets' and email to be " +
                             "configured. Only applies when using the internal identity provider. The " +
                             "default value for this property is false.")
    public boolean isAllowLockedAccountPasswordReset() {
        return allowLockedAccountPasswordReset;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("How long a user must wait between requesting one password reset email and " +
                             "the next. This stops the unauthenticated 'Forgot password' endpoint being " +
                             "used to send mail to someone's address over and over. " +
                             "Requests for an address that has no account are not limited " +
                             "here because no mail is sent for them. Only applies when using the " +
                             "internal identity provider.")
    public StroomDuration getPasswordResetRequestCooldown() {
        return passwordResetRequestCooldown;
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
               ", failedLoginLockDuration=" + failedLoginLockDuration +
               ", reactivateInactiveAccountsOnLogin=" + reactivateInactiveAccountsOnLogin +
               ", allowLockedAccountPasswordReset=" + allowLockedAccountPasswordReset +
               ", passwordResetRequestCooldown=" + passwordResetRequestCooldown +
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
