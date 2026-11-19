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

package stroom.query.client.result.table;

import stroom.dashboard.shared.ColumnValue;
import stroom.preferences.client.UserPreferencesManager;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestColumnValueRowStyles {

    private static final String FIRST_UNAVAILABLE = "column-values-filter__row--first-unavailable";

    private final ColumnValueRowStyles rowStyles = new ColumnValueRowStyles(mockUserPreferencesManager());

    @Test
    void testLineAboveFirstUnavailable() {
        rowStyles.setAvailableCount(2);

        assertThat(rowStyles.getStyleNames(value("a", true), 0)).isEmpty();
        assertThat(rowStyles.getStyleNames(value("b", true), 1)).isEmpty();
        assertThat(rowStyles.getStyleNames(value("c", false), 2)).isEqualTo(FIRST_UNAVAILABLE);
        assertThat(rowStyles.getStyleNames(value("d", false), 3)).isEmpty();
    }

    @Test
    void testLineOnLaterPage() {
        // The index is across all pages, so the first unavailable value may be on a later page
        rowStyles.setAvailableCount(150);

        assertThat(rowStyles.getStyleNames(value("a", true), 149)).isEmpty();
        assertThat(rowStyles.getStyleNames(value("b", false), 150)).isEqualTo(FIRST_UNAVAILABLE);
    }

    @Test
    void testNoLineWhenNoneAvailable() {
        // Nothing above to separate from
        rowStyles.setAvailableCount(0);

        assertThat(rowStyles.getStyleNames(value("a", false), 0)).isEmpty();
        assertThat(rowStyles.getStyleNames(value("b", false), 1)).isEmpty();
    }

    @Test
    void testNoLineWhenAllAvailable() {
        rowStyles.setAvailableCount(2);

        assertThat(rowStyles.getStyleNames(value("a", true), 0)).isEmpty();
        assertThat(rowStyles.getStyleNames(value("b", true), 1)).isEmpty();
    }

    @Test
    void testNoLineOnAvailableValueAtCount() {
        // A stale count must not draw a line above a value that is available
        rowStyles.setAvailableCount(1);

        assertThat(rowStyles.getStyleNames(value("b", true), 1)).isEmpty();
    }

    @Test
    void testNullRow() {
        rowStyles.setAvailableCount(0);

        assertThat(rowStyles.getStyleNames(null, 0)).isEmpty();
    }

    private static UserPreferencesManager mockUserPreferencesManager() {
        // The manager's class uses GWT.create when it loads, which only works in a browser
        GWTMockUtilities.disarm();
        try {
            return Mockito.mock(UserPreferencesManager.class);
        } finally {
            GWTMockUtilities.restore();
        }
    }

    private static ColumnValue value(final String value, final boolean available) {
        return new ColumnValue(value, null, available);
    }
}
