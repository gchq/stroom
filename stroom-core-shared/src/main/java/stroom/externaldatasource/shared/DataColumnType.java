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

package stroom.externaldatasource.shared;

import stroom.docref.HasDisplayValue;

/// How the values of a [DataColumn] in stored data, such as CSV, are read.
public enum DataColumnType implements HasDisplayValue {
    /// Values are kept as text.
    TEXT("Text"),
    /// Whole numbers.
    LONG("Long"),
    /// Numbers that may have a fraction.
    DOUBLE("Double"),
    /// Dates and times in ISO 8601, e.g. `2026-11-11` or `2026-11-11T09:30:00Z`, with a four-digit year.
    /// A space may stand for the `T`. A time with no zone is taken as UTC; a zone is `Z` or an offset
    /// such as `+01:00`, `+0100` or `+01`.
    DATE("Date"),
    /// `true` or `false`, in any case.
    BOOLEAN("Boolean");

    private final String displayValue;

    DataColumnType(final String displayValue) {
        this.displayValue = displayValue;
    }

    /// Gets the name of the type as shown to users.
    ///
    /// @return The display name.
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
