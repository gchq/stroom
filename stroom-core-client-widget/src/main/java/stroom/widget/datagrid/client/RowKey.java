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

package stroom.widget.datagrid.client;

import java.util.Objects;

/// Identifies a rendered row of a [DataGridWidget]: the row's key, plus which occurrence of that key
/// on the page it is, so a page that shows the same row twice still has two distinct rows.
final class RowKey {

    private final Object key;
    private final int occurrence;

    /// @param key        The row's key.
    /// @param occurrence How many rows with the same key come before it on the page.
    RowKey(final Object key, final int occurrence) {
        this.key = key;
        this.occurrence = occurrence;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RowKey)) {
            return false;
        }
        final RowKey that = (RowKey) o;
        return occurrence == that.occurrence && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, occurrence);
    }
}
