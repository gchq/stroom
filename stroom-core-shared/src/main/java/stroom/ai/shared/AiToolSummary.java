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

/// One of Stroom's own tools, as an administrator choosing what a tool policy grants sees it.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiToolSummary {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final AiToolCategory category;
    @JsonProperty
    private final boolean readOnly;
    @JsonProperty
    private final boolean readOnlyCalls;
    @JsonProperty
    private final String description;

    /// @param name        The tool's name.
    /// @param category    Its category.
    /// @param readOnly    True if it cannot change anything, so a read-only policy grants it.
    /// @param description What it does, as a client outside the chat is told.
    public AiToolSummary(final String name,
                         final AiToolCategory category,
                         final boolean readOnly,
                         final String description) {
        this(name, category, readOnly, false, description);
    }

    /// @param name          The tool's name.
    /// @param category      Its category.
    /// @param readOnly      True if it cannot change anything, so a read-only policy grants it. Null is false.
    /// @param readOnlyCalls True if it can change things but some of its calls cannot, so a read-only policy
    ///                      grants it for those calls only (e.g. memory's list and read). Null is false.
    /// @param description   What it does, as a client outside the chat is told.
    @JsonCreator
    public AiToolSummary(@JsonProperty("name") final String name,
                         @JsonProperty("category") final AiToolCategory category,
                         @JsonProperty("readOnly") final Boolean readOnly,
                         @JsonProperty("readOnlyCalls") final Boolean readOnlyCalls,
                         @JsonProperty("description") final String description) {
        this.name = name;
        this.category = category;
        this.readOnly = Boolean.TRUE.equals(readOnly);
        this.readOnlyCalls = Boolean.TRUE.equals(readOnlyCalls);
        this.description = description;
    }

    /// @return The tool's name.
    public String getName() {
        return name;
    }

    /// @return Its category.
    public AiToolCategory getCategory() {
        return category;
    }

    /// @return True if it cannot change anything, so a read-only policy grants it.
    public boolean isReadOnly() {
        return readOnly;
    }

    /// @return True if it can change things but some of its calls cannot, so a read-only policy grants it for
    /// those calls only.
    public boolean isReadOnlyCalls() {
        return readOnlyCalls;
    }

    /// @return What it does, as a client outside the chat is told.
    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiToolSummary that = (AiToolSummary) o;
        return readOnly == that.readOnly
               && readOnlyCalls == that.readOnlyCalls
               && Objects.equals(name, that.name)
               && category == that.category
               && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, category, readOnly, readOnlyCalls, description);
    }
}
