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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// An error, warning or message that a step raised for a pipeline element, as the stepping screen shows it.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiSteppingError {

    @JsonProperty
    private final String severity;
    @JsonProperty
    private final String pane;
    @JsonProperty
    private final Integer lineNo;
    @JsonProperty
    private final Integer colNo;
    @JsonProperty
    private final String message;

    /// @param severity The severity, e.g. `ERROR`.
    /// @param pane     The pane it is about: `CODE`, `INPUT`, `OUTPUT` or `GENERIC`, or null if not known.
    /// @param lineNo   The one based line it is on, or null.
    /// @param colNo    The one based column it is at, or null.
    /// @param message  The message.
    @JsonCreator
    public AiSteppingError(@JsonProperty("severity") final String severity,
                           @JsonProperty("pane") final String pane,
                           @JsonProperty("lineNo") final Integer lineNo,
                           @JsonProperty("colNo") final Integer colNo,
                           @JsonProperty("message") final String message) {
        this.severity = severity;
        this.pane = pane;
        this.lineNo = lineNo;
        this.colNo = colNo;
        this.message = message;
    }

    /// @return The severity, e.g. `ERROR`.
    public String getSeverity() {
        return severity;
    }

    /// @return The pane it is about: `CODE`, `INPUT`, `OUTPUT` or `GENERIC`, or null if not known.
    public String getPane() {
        return pane;
    }

    /// @return The one based line it is on, or null.
    public Integer getLineNo() {
        return lineNo;
    }

    /// @return The one based column it is at, or null.
    public Integer getColNo() {
        return colNo;
    }

    /// @return The message.
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiSteppingError that = (AiSteppingError) o;
        return Objects.equals(severity, that.severity)
               && Objects.equals(pane, that.pane)
               && Objects.equals(lineNo, that.lineNo)
               && Objects.equals(colNo, that.colNo)
               && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, pane, lineNo, colNo, message);
    }

    @Override
    public String toString() {
        return severity + " " + pane + " [" + lineNo + ":" + colNo + "] " + message;
    }
}
