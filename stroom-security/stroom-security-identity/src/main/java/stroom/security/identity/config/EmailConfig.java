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

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class EmailConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_SMTP = "smtp";

    @NotNull
    @JsonProperty(PROP_NAME_SMTP)
    private final SmtpConfig smtpConfig;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The from email address to use in password reset emails.")
    private final String fromAddress;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The from name to use in password reset emails.")
    private final String fromName;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The subject to use in password reset emails.")
    private final String passwordResetSubject;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The email body text to use in password reset emails.")
    private final String passwordResetText;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The URL to  perform a password reset that will be included in the text of a " +
            "password reset emails")
    private final String passwordResetUrl;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Enables/disables the sending of password reset emails. This should only be " +
            "enabled when all the SMTP server configuration has been set.")
    // Defaults to false because this feature needs to be configured and actively turned on before it works.
    private final boolean allowPasswordResets;

    public EmailConfig() {
        smtpConfig = new SmtpConfig();
        fromAddress = "noreply@stroom";
        fromName = "Stroom User Accounts";
        passwordResetSubject = "Password reset for Stroom";
        passwordResetText = "A password reset has been requested for this email address. Please visit " +
                "the following URL to reset your password: %s.";
        passwordResetUrl = "/s/resetPassword/?user=%s&token=%s";
        // Defaults to false because this feature needs to be configured and actively turned on before it works.
        allowPasswordResets = false;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public EmailConfig(@JsonProperty("smtp") final SmtpConfig smtpConfig,
                       @JsonProperty("fromAddress") final String fromAddress,
                       @JsonProperty("fromName") final String fromName,
                       @JsonProperty("passwordResetSubject") final String passwordResetSubject,
                       @JsonProperty("passwordResetText") final String passwordResetText,
                       @JsonProperty("passwordResetUrl") final String passwordResetUrl,
                       @JsonProperty("allowPasswordResets") final boolean allowPasswordResets) {
        this.smtpConfig = smtpConfig;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.passwordResetSubject = passwordResetSubject;
        this.passwordResetText = passwordResetText;
        this.passwordResetUrl = passwordResetUrl;
        this.allowPasswordResets = allowPasswordResets;
    }

    @JsonProperty(PROP_NAME_SMTP)
    public SmtpConfig getSmtpConfig() {
        return smtpConfig;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getPasswordResetSubject() {
        return passwordResetSubject;
    }

    public String getPasswordResetText() {
        return passwordResetText;
    }

    public String getFromName() {
        return fromName;
    }

    public String getPasswordResetUrl() {
        return passwordResetUrl;
    }

    public boolean isAllowPasswordResets() {
        return allowPasswordResets;
    }

    @Override
    public String toString() {
        return "EmailConfig{" +
                "smtpConfig=" + smtpConfig +
                ", fromAddress='" + fromAddress + '\'' +
                ", fromName='" + fromName + '\'' +
                ", passwordResetSubject='" + passwordResetSubject + '\'' +
                ", passwordResetText='" + passwordResetText + '\'' +
                ", passwordResetUrl='" + passwordResetUrl + '\'' +
                ", allowPasswordResets=" + allowPasswordResets +
                '}';
    }
}
