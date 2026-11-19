/*
 * Copyright 2025 Crown Copyright
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

package stroom.query.client.result.table;

import stroom.dashboard.shared.ColumnValue;
import stroom.preferences.client.UserPreferencesManager;

/// Styles a column value's row: by the column's conditional formatting, and with a line above the first value
/// that is not available (see [ColumnValue#isAvailable()]).
public class ColumnValueRowStyles extends ConditionalRowStyles<ColumnValue> {

    private static final String FIRST_UNAVAILABLE = "column-values-filter__row--first-unavailable";

    private long availableCount;

    /// @param userPreferencesManager Gives the theme the conditional formatting's row styles use.
    public ColumnValueRowStyles(final UserPreferencesManager userPreferencesManager) {
        super(userPreferencesManager,
                row -> row == null
                        ? null
                        : row.getMatchingRule());
    }

    /// Sets the number of available values across all pages. Available values come first, so the value at
    /// this index is the first that is not available and has a line drawn above it.
    ///
    /// @param availableCount The number of available values.
    public void setAvailableCount(final long availableCount) {
        this.availableCount = availableCount;
    }

    @Override
    public String getStyleNames(final ColumnValue row, final int rowIndex) {
        final String styleNames = super.getStyleNames(row, rowIndex);
        // No line when none are available, as there is nothing above it to separate
        if (row != null && !row.isAvailable() && availableCount > 0 && rowIndex == availableCount) {
            return styleNames == null || styleNames.isEmpty()
                    ? FIRST_UNAVAILABLE
                    : styleNames + " " + FIRST_UNAVAILABLE;
        }
        return styleNames;
    }
}
