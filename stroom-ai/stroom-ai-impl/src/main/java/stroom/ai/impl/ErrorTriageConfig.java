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

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/// How the tools triage the errors a pipeline wrote or stepping met, as blocking, review or benign (see
/// [stroom.ai.impl.tools.ErrorTriage]): Stroom's own rules are bundled, and these come before them.
@JsonPropertyOrder(alphabetic = true)
public class ErrorTriageConfig extends AbstractConfig implements IsStroomConfig {

    private final List<String> rules;

    public ErrorTriageConfig() {
        rules = List.of();
    }

    @JsonCreator
    public ErrorTriageConfig(@JsonProperty("rules") final List<String> rules) {
        this.rules = List.copyOf(NullSafe.list(rules));
    }

    /// @return Environment-specific triage rules, each a YAML or JSON mapping, checked in order before the bundled
    /// ones.
    @JsonPropertyDescription("Environment-specific rules for triaging pipeline errors as blocking (must fix), "
                             + "review (may be caused by the pipeline's own content) or benign (report only), "
                             + "checked in order before Stroom's own rules; the first match decides. Each is a "
                             + "YAML or JSON mapping with any of severity (a list of FATAL, ERROR, WARNING, INFO), "
                             + "element (a glob on the element id), own (true for an element the pipeline sets "
                             + "itself, false for one it inherits), message (a regular expression), and the "
                             + "required class and reason, e.g. {element: decorationFilter, severity: [WARNING, "
                             + "INFO], message: 'lookup', class: benign, reason: 'User decoration miss for "
                             + "accounts not in reference data'}.")
    @JsonProperty("rules")
    public List<String> getRules() {
        return rules;
    }

    @Override
    public String toString() {
        return "ErrorTriageConfig{" +
               "rules=" + rules +
               '}';
    }
}
