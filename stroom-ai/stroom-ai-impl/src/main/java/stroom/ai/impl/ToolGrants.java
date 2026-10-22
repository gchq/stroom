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

import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/// What a user's tool policies grant them, taken together: for each category, whether they may use its tools that
/// only read, or all of them. A category not granted has no access.
///
/// @param byCategory The access each granted category has.
public record ToolGrants(Map<AiToolCategory, AiToolAccess> byCategory) {

    /// Grants nothing.
    public static final ToolGrants NONE = new ToolGrants(Map.of());

    public ToolGrants {
        byCategory = byCategory.isEmpty()
                ? Map.of()
                : Map.copyOf(new EnumMap<>(byCategory));
    }

    /// @return Every category, read and write. For tests.
    public static ToolGrants all() {
        final Map<AiToolCategory, AiToolAccess> map = new EnumMap<>(AiToolCategory.class);
        for (final AiToolCategory category : AiToolCategory.values()) {
            map.put(category, AiToolAccess.READ_WRITE);
        }
        return new ToolGrants(map);
    }

    /// @param category A category.
    /// @return The access granted to it, if any.
    public Optional<AiToolAccess> access(final AiToolCategory category) {
        return Optional.ofNullable(byCategory.get(category));
    }

    /// @param category        A tool's category.
    /// @param readOnly        True if the tool cannot change anything.
    /// @param hasReadOnlyCalls True if some calls of the tool cannot change anything (e.g. memory's `read`).
    /// @return True if the tool may be offered: its category is granted, and either read and write, or the tool
    /// has calls that only read.
    public boolean offers(final AiToolCategory category, final boolean readOnly, final boolean hasReadOnlyCalls) {
        final AiToolAccess access = byCategory.get(category);
        return access == AiToolAccess.READ_WRITE
               || (access == AiToolAccess.READ_ONLY && (readOnly || hasReadOnlyCalls));
    }

    /// @param category     A tool's category.
    /// @param readOnlyCall True if this call of the tool cannot change anything.
    /// @return True if the call may be made.
    public boolean allowsCall(final AiToolCategory category, final boolean readOnlyCall) {
        final AiToolAccess access = byCategory.get(category);
        return access == AiToolAccess.READ_WRITE
               || (access == AiToolAccess.READ_ONLY && readOnlyCall);
    }

    /// @return True if nothing is granted.
    public boolean isEmpty() {
        return byCategory.isEmpty();
    }
}
