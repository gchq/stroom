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

package stroom.widget.datagrid.client.model;

import java.util.List;

/// Where the keyboard row goes when the rows on the page change (design R-S12), replicating the
/// forked GWT `HasDataPresenter.resolvePendingState`, as measured in the workbench in Phase 0.
///
/// The keyboard row is remembered by its row's key as well as its index. When new rows arrive it
/// moves to the row with the same key nearest to its old index ([#bestMatch]); if no row has that
/// key it goes back to the first row. It is always within the page, and is `0` on an empty page
/// ([#clamp]).
public final class KeyboardRow {

    private KeyboardRow() {
        // Utility class.
    }

    /// @param index    A keyboard row index.
    /// @param rowCount The number of rows on the page.
    /// @return The index clamped to the page: from `0` to `rowCount - 1`, or `0` if it is empty.
    public static int clamp(final int index, final int rowCount) {
        return Math.max(0, Math.min(index, rowCount - 1));
    }

    /// Find where a row has gone, as `HasDataPresenter.findIndexOfBestMatch` does.
    ///
    /// @param key          The key of the keyboard row's row before the change, or `null`.
    /// @param initialIndex The keyboard row's index before the change.
    /// @param keys         The keys of the rows on the page now. `null` for a placeholder.
    /// @return The index of the row with that key nearest to `initialIndex` (the earlier one on a
    /// tie), or `-1` if there is none or the key is `null`.
    public static int bestMatch(final Object key, final int initialIndex, final List<?> keys) {
        if (key == null) {
            return -1;
        }
        int best = -1;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            if (key.equals(keys.get(i))) {
                final int diff = Math.abs(initialIndex - i);
                if (diff < bestDiff) {
                    best = i;
                    bestDiff = diff;
                }
            }
        }
        return best;
    }
}
