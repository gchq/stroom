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
    /// One row per match; rows of the first source with no match are left out.
    INNER("join", "inner join"),
    /// One row per match; rows of the first source with no match are given once, with blank joined
    /// fields.
    LEFT("left join", "left outer join"),
    /// Each row of the first source with at least one match, once, with no joined fields.
    SEMI("semi join", "left semi join"),
    /// Each row of the first source with no match, with no joined fields.
    ANTI("anti join", "left anti join"),
    /// One row per match; rows of the joined source with no match are given once, with blank fields
    /// of the first source, when the first source has been searched.
    RIGHT("right join", "right outer join"),
    /// One row per match; rows of either source with no match are given once, with blank fields of
    /// the other, those of the joined source when the first source has been searched.
    FULL("full join", "full outer join");

    private final String displayValue;
    private final String longSpelling;

    JoinType(final String displayValue, final String longSpelling) {
        this.displayValue = displayValue;
        this.longSpelling = longSpelling;
    }

    /// Gets the kind of join a join line's words spell, as the tokeniser and parser both read them.
    ///
    /// @param words The words of the kind and `join`, in lower case and separated by single spaces,
    ///              e.g. `left outer join`.
    /// @return The kind of join, or `null` if the words spell none.
    public static JoinType fromSpelling(final String words) {
        for (final JoinType type : values()) {
            if (type.displayValue.equals(words) || type.longSpelling.equals(words)) {
                return type;
            }
        }
        return null;
    }

    /// Gets the other way of writing this kind of join, as in SQL.
    ///
    /// @return The words, e.g. `left outer join`.
    public String getLongSpelling() {
        return longSpelling;
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
    /// @return True for inner, left, right and full joins.
    public boolean addsFields() {
        return this != SEMI && this != ANTI;
    }

    /// Finds whether this kind of join gives the joined source's rows that never matched, once the
    /// first source has been searched.
    ///
    /// @return True for right and full joins.
    public boolean givesUnmatchedJoinedRows() {
        return this == RIGHT || this == FULL;
    }

    /// Finds whether a condition that names only the joined source's fields can be moved into the
    /// joined source's own search without changing the result.
    ///
    /// @return True for inner and semi joins, where a joined row that fails the condition is dropped
    /// either way. False for left, anti, right and full joins, where moving it would change which rows
    /// count as unmatched.
    public boolean allowsJoinedConditionPushDown() {
        return this == INNER || this == SEMI;
    }
}
