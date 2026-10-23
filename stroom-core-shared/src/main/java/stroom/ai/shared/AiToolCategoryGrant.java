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

/// A category of tools that a tool policy grants, and how much of it: only the tools (and calls) that cannot
/// change anything, or all of them.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiToolCategoryGrant {

    @JsonProperty
    private final AiToolCategory category;
    @JsonProperty
    private final AiToolAccess access;

    /// @param category The category of tools.
    /// @param access   How much of it is granted; read-only if null.
    @JsonCreator
    public AiToolCategoryGrant(@JsonProperty("category") final AiToolCategory category,
                               @JsonProperty("access") final AiToolAccess access) {
        this.category = category;
        this.access = Objects.requireNonNullElse(access, AiToolAccess.READ_ONLY);
    }

    /// @param category The category of tools.
    /// @param access   How much of it is granted.
    /// @return The grant.
    public static AiToolCategoryGrant of(final AiToolCategory category, final AiToolAccess access) {
        return new AiToolCategoryGrant(category, access);
    }

    /// @return The category of tools.
    public AiToolCategory getCategory() {
        return category;
    }

    /// @return How much of it is granted.
    public AiToolAccess getAccess() {
        return access;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiToolCategoryGrant that = (AiToolCategoryGrant) o;
        return category == that.category
               && access == that.access;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, access);
    }

    @Override
    public String toString() {
        return category + "=" + access;
    }
}
