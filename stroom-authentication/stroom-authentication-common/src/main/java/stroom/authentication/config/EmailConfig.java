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

import javax.validation.constraints.NotNull;

public class EmailConfig extends AbstractConfig {
    public static final String PROP_NAME_SMTP = "smtp";

    @NotNull
    @JsonProperty(PROP_NAME_SMTP)
    private SmtpConfig smtpConfig = new SmtpConfig();

    @NotNull
    @JsonProperty
    private String fromAddress = "noreply@stroom";

    @NotNull
    @JsonProperty
    private String fromName = "Stroom User Accounts";

    @NotNull
    @JsonProperty
    private String passwordResetSubject = "Password reset for Stroom";

    @NotNull
    @JsonProperty
    private String passwordResetText = "A password reset has been requested for this email address. Please visit the following URL to reset your password: %s.";

    @NotNull
    @JsonProperty
    private String passwordResetUrl = "/s/resetPassword/?user=%s&token=%s";

    @NotNull
    @JsonProperty
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
}
