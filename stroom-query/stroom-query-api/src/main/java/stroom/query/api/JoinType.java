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

package stroom.query.api;

import stroom.docref.HasDisplayValue;

/// The kind of a StroomQL join, which decides which rows of the first source are kept and whether
/// the fields of the joined source are added to them.
public enum JoinType implements HasDisplayValue {
    /// One row per match; rows of the first source with no match are dropped.
    INNER("join"),
    /// One row per match; rows of the first source with no match are kept, with blank joined fields.
    LEFT("left join"),
    /// Each row of the first source with at least one match, once, with no joined fields.
    KEEP("keep join"),
    /// Each row of the first source with no match, with no joined fields.
    DROP("drop join");

    private final String displayValue;

    JoinType(final String displayValue) {
        this.displayValue = displayValue;
    }

    /// Gets the StroomQL words for this kind of join.
    ///
    /// @return The words, e.g. `left join`.
    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /// Finds whether this kind of join adds the joined source's fields to its rows.
    ///
    /// @return True for inner and left joins.
    public boolean addsFields() {
        return this == INNER || this == LEFT;
    }

    /// Finds whether a condition that names only the joined source's fields can be moved into the
    /// joined source's own search without changing the result.
    ///
    /// @return True for inner and keep joins, where a joined row that fails the condition is dropped
    /// either way. False for left and drop joins, where moving it would change which rows count as
    /// unmatched.
    public boolean allowsJoinedConditionPushDown() {
        return this == INNER || this == KEEP;
    }
}
