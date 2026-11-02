/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl;

import stroom.util.shared.IsAtomicConfigValue;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// One of the environment's error triage rules, as set in the config (a YAML mapping in `config.yml`, an item of
/// a JSON array in the Properties screen), with the fields of Stroom's bundled rules (`error_rules.yaml`). Held as
/// a list in [ErrorTriageConfig#getRules()]; [stroom.ai.impl.tools.ErrorTriage] checks the class and reason.
@NotInjectableConfig
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"severity", "element", "own", "message", "class", "reason", "cause"})
public class ErrorTriageRuleConfig implements IsAtomicConfigValue {

    @JsonProperty
    private final List<String> severity;
    @JsonProperty
    private final String element;
    @JsonProperty
    private final Boolean own;
    @JsonProperty
    private final String message;
    @JsonProperty("class")
    private final String classification;
    @JsonProperty
    private final String reason;
    @JsonProperty
    private final Boolean cause;

    /// @param severity       The severities it matches (FATAL, ERROR, WARNING, INFO), or null for any.
    /// @param element        A glob on the element id, or null for any.
    /// @param own            True for an element the pipeline sets itself, false for one it inherits, or null.
    /// @param message        A regular expression found in the message, or null for any.
    /// @param classification blocking, review or benign.
    /// @param reason         Why, for the agent and the user.
    /// @param cause          True if the error loses text, so the errors after it may be its consequences, or null.
    @JsonCreator
    public ErrorTriageRuleConfig(@JsonProperty("severity") final List<String> severity,
                                 @JsonProperty("element") final String element,
                                 @JsonProperty("own") final Boolean own,
                                 @JsonProperty("message") final String message,
                                 @JsonProperty("class") final String classification,
                                 @JsonProperty("reason") final String reason,
                                 @JsonProperty("cause") final Boolean cause) {
        this.severity = NullSafe.get(severity, List::copyOf);
        this.element = element;
        this.own = own;
        this.message = message;
        this.classification = classification;
        this.reason = reason;
        this.cause = cause;
    }

    /// @return The severities it matches, or null for any.
    public List<String> getSeverity() {
        return severity;
    }

    /// @return A glob on the element id, or null for any.
    public String getElement() {
        return element;
    }

    /// @return True for an element the pipeline sets itself, false for one it inherits, or null for either.
    public Boolean getOwn() {
        return own;
    }

    /// @return A regular expression found in the message, or null for any.
    public String getMessage() {
        return message;
    }

    /// @return blocking, review or benign.
    @JsonProperty("class")
    public String getClassification() {
        return classification;
    }

    /// @return Why, for the agent and the user.
    public String getReason() {
        return reason;
    }

    /// @return True if the error loses text, so the errors after it may be its consequences, or null.
    public Boolean getCause() {
        return cause;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ErrorTriageRuleConfig that = (ErrorTriageRuleConfig) o;
        return Objects.equals(severity, that.severity)
               && Objects.equals(element, that.element)
               && Objects.equals(own, that.own)
               && Objects.equals(message, that.message)
               && Objects.equals(classification, that.classification)
               && Objects.equals(reason, that.reason)
               && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, element, own, message, classification, reason, cause);
    }

    @Override
    public String toString() {
        return "ErrorTriageRuleConfig{" +
               "severity=" + severity +
               ", element='" + element + '\'' +
               ", own=" + own +
               ", message='" + message + '\'' +
               ", class='" + classification + '\'' +
               ", reason='" + reason + '\'' +
               ", cause=" + cause +
               '}';
    }
}
