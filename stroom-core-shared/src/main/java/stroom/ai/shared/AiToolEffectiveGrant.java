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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// A category of tools that a user's or group's tool policies grant it, with the enabled policies covering it that
/// grant the category, by the access each grants.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiToolEffectiveGrant {

    @JsonProperty
    private final AiToolCategory category;
    @JsonProperty
    private final List<String> readWriteBy;
    @JsonProperty
    private final List<String> readOnlyBy;

    /// @param category    The category of tools.
    /// @param readWriteBy The names of the policies granting it read and write.
    /// @param readOnlyBy  The names of the policies granting it read-only.
    @JsonCreator
    public AiToolEffectiveGrant(@JsonProperty("category") final AiToolCategory category,
                                @JsonProperty("readWriteBy") final List<String> readWriteBy,
                                @JsonProperty("readOnlyBy") final List<String> readOnlyBy) {
        this.category = category;
        this.readWriteBy = NullSafe.list(readWriteBy);
        this.readOnlyBy = NullSafe.list(readOnlyBy);
    }

    /// @return The category of tools.
    public AiToolCategory getCategory() {
        return category;
    }

    /// @return The names of the policies granting it read and write.
    public List<String> getReadWriteBy() {
        return readWriteBy;
    }

    /// @return The names of the policies granting it read-only.
    public List<String> getReadOnlyBy() {
        return readOnlyBy;
    }

    /// @return The widest access granted: read and write if any policy grants that.
    @JsonIgnore
    public AiToolAccess getAccess() {
        return readWriteBy.isEmpty()
                ? AiToolAccess.READ_ONLY
                : AiToolAccess.READ_WRITE;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiToolEffectiveGrant that = (AiToolEffectiveGrant) o;
        return category == that.category
               && Objects.equals(readWriteBy, that.readWriteBy)
               && Objects.equals(readOnlyBy, that.readOnlyBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, readWriteBy, readOnlyBy);
    }

    @Override
    public String toString() {
        return category + "=" + getAccess() + " (read and write by " + readWriteBy
               + ", read-only by " + readOnlyBy + ")";
    }
}
