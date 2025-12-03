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

package stroom.analytics.impl;

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

    public EmailConfig() {
        smtpConfig = new SmtpConfig();
        fromAddress = "noreply@stroom";
        fromName = "Stroom Analytics";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public EmailConfig(@JsonProperty("smtp") final SmtpConfig smtpConfig,
                       @JsonProperty("fromAddress") final String fromAddress,
                       @JsonProperty("fromName") final String fromName) {
        this.smtpConfig = smtpConfig;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @JsonProperty(PROP_NAME_SMTP)
    public SmtpConfig getSmtpConfig() {
        return smtpConfig;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    @Override
    public String toString() {
        return "EmailConfig{" +
               "smtpConfig=" + smtpConfig +
               ", fromAddress='" + fromAddress + '\'' +
               ", fromName='" + fromName + '\'' +
               '}';
    }
}
