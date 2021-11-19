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

package stroom.security.identity.config;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.validation.constraints.NotNull;

public class EmailConfig extends AbstractConfig {

    public static final String PROP_NAME_SMTP = "smtp";

    @NotNull
    @JsonProperty(PROP_NAME_SMTP)
    private SmtpConfig smtpConfig = new SmtpConfig();

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The from email address to use in password reset emails.")
    private String fromAddress = "noreply@stroom";

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The from name to use in password reset emails.")
    private String fromName = "Stroom User Accounts";

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The subject to use in password reset emails.")
    private String passwordResetSubject = "Password reset for Stroom";

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The email body text to use in password reset emails.")
    private String passwordResetText = "A password reset has been requested for this email address. Please visit " +
            "the following URL to reset your password: %s.";

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The URL to  perform a password reset that will be included in the text of a " +
            "password reset emails")
    private String passwordResetUrl = "/s/resetPassword/?user=%s&token=%s";

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Enables/disables the sending of password reset emails. This should only be " +
            "enabled when all the SMTP server configuration has been set.")
    // Defaults to false because this feature needs to be configured and actively turned on before it works.
    private boolean allowPasswordResets = false;

    @JsonProperty(PROP_NAME_SMTP)
    public SmtpConfig getSmtpConfig() {
        return smtpConfig;
    }

    public void setSmtpConfig(SmtpConfig smtpConfig) {
        this.smtpConfig = smtpConfig;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(final String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getPasswordResetSubject() {
        return passwordResetSubject;
    }

    public void setPasswordResetSubject(final String passwordResetSubject) {
        this.passwordResetSubject = passwordResetSubject;
    }

    public String getPasswordResetText() {
        return passwordResetText;
    }

    public void setPasswordResetText(final String passwordResetText) {
        this.passwordResetText = passwordResetText;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(final String fromName) {
        this.fromName = fromName;
    }

    public String getPasswordResetUrl() {
        return passwordResetUrl;
    }

    public void setPasswordResetUrl(final String passwordResetUrl) {
        this.passwordResetUrl = passwordResetUrl;
    }

    public boolean isAllowPasswordResets() {
        return allowPasswordResets;
    }

    public void setAllowPasswordResets(final boolean allowPasswordResets) {
        this.allowPasswordResets = allowPasswordResets;
    }
}
