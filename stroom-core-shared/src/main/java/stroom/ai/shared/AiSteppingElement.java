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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// An element of the pipeline being stepped, as the stepping screen shows it: its id and type, the document it
/// uses, and how many errors and warnings the current step raised for it. Only the selected element's messages are
/// given in full.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiSteppingElement {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final String type;
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final boolean hasCode;
    @JsonProperty
    private final boolean dirty;
    @JsonProperty
    private final int errorCount;
    @JsonProperty
    private final int warningCount;
    @JsonProperty
    private final List<AiSteppingError> errors;

    /// @param id           The element's id, as named in the pipeline.
    /// @param type         The element's type, e.g. `XSLTFilter`.
    /// @param docRef       The document it uses (e.g. its XSLT), or null if it uses none or it isn't known.
    /// @param hasCode      True if it has code shown in an editor (e.g. an XSLT or text converter).
    /// @param dirty        True if its code has unsaved changes.
    /// @param errorCount   How many errors (fatal ones included) the current step raised for it.
    /// @param warningCount How many warnings the current step raised for it.
    /// @param errors       Its messages, for the selected element only, or null.
    @JsonCreator
    public AiSteppingElement(@JsonProperty("id") final String id,
                             @JsonProperty("type") final String type,
                             @JsonProperty("docRef") final DocRef docRef,
                             @JsonProperty("hasCode") final Boolean hasCode,
                             @JsonProperty("dirty") final Boolean dirty,
                             @JsonProperty("errorCount") final Integer errorCount,
                             @JsonProperty("warningCount") final Integer warningCount,
                             @JsonProperty("errors") final List<AiSteppingError> errors) {
        this.id = id;
        this.type = type;
        this.docRef = docRef;
        this.hasCode = hasCode != null && hasCode;
        this.dirty = dirty != null && dirty;
        this.errorCount = errorCount == null
                ? 0
                : errorCount;
        this.warningCount = warningCount == null
                ? 0
                : warningCount;
        this.errors = errors == null
                ? null
                : new ArrayList<>(errors);
    }

    /// @return The element's id, as named in the pipeline.
    public String getId() {
        return id;
    }

    /// @return The element's type, e.g. `XSLTFilter`.
    public String getType() {
        return type;
    }

    /// @return The document it uses, or null if it uses none or it isn't known.
    public DocRef getDocRef() {
        return docRef;
    }

    /// @return True if it has code shown in an editor.
    public boolean isHasCode() {
        return hasCode;
    }

    /// @return True if its code has unsaved changes.
    public boolean isDirty() {
        return dirty;
    }

    /// @return How many errors (fatal ones included) the current step raised for it.
    public int getErrorCount() {
        return errorCount;
    }

    /// @return How many warnings the current step raised for it.
    public int getWarningCount() {
        return warningCount;
    }

    /// @return Its messages, for the selected element only; empty for the others.
    public List<AiSteppingError> getErrors() {
        return errors == null
                ? Collections.emptyList()
                : errors;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiSteppingElement that = (AiSteppingElement) o;
        return hasCode == that.hasCode
               && dirty == that.dirty
               && errorCount == that.errorCount
               && warningCount == that.warningCount
               && Objects.equals(id, that.id)
               && Objects.equals(type, that.type)
               && Objects.equals(docRef, that.docRef)
               && Objects.equals(getErrors(), that.getErrors());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, docRef, hasCode, dirty, errorCount, warningCount, getErrors());
    }

    @Override
    public String toString() {
        return "AiSteppingElement{id='" + id + "', type='" + type + "', docRef=" + docRef + ", errorCount="
               + errorCount + ", warningCount=" + warningCount + "}";
    }
}
