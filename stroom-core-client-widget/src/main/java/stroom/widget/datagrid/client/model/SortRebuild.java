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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A grid's sort while its columns are removed and added again, e.g. a list that makes its columns
/// afresh when it becomes read only. A removed column's entry leaves the sort, so the sort never
/// names a column the grid no longer has, and is set aside; a column added with the same sort field
/// (its key) takes it back, in its place. What isn't taken back by the end of the rebuild is
/// forgotten ([#finish()]).
///
/// @param <C> The type of the column objects.
public final class SortRebuild<C> {

    private final Function<C, String> key;
    // The entries set aside, each with its place in the sort when it was removed
    private final List<SetAside> setAside = new ArrayList<>();

    /// @param key A column's sort field, or null if it has none (it can't then be taken back).
    public SortRebuild(final Function<C, String> key) {
        this.key = Objects.requireNonNull(key);
    }

    /// Takes a removed column out of the sort, setting its entry aside if it has a sort field.
    ///
    /// @param sort   The sort, primary column first.
    /// @param column The column removed.
    /// @return The sort without it.
    public List<SortEntry<C>> remove(final List<SortEntry<C>> sort, final C column) {
        final List<SortEntry<C>> result = new ArrayList<>(sort);
        for (int i = 0; i < result.size(); i++) {
            final SortEntry<C> entry = result.get(i);
            if (entry.getColumn().equals(column)) {
                result.remove(i);
                final String columnKey = key.apply(column);
                if (columnKey != null) {
                    setAside.add(new SetAside(columnKey, i, entry.isAscending()));
                }
                return result;
            }
        }
        return result;
    }

    /// Gives an added column the entry set aside for its sort field, if there is one, in its place.
    ///
    /// @param sort   The sort, primary column first.
    /// @param column The column added.
    /// @return The sort, with the column in it if it took an entry back.
    public List<SortEntry<C>> add(final List<SortEntry<C>> sort, final C column) {
        final String columnKey = key.apply(column);
        if (columnKey == null) {
            return sort;
        }
        final Iterator<SetAside> iterator = setAside.iterator();
        while (iterator.hasNext()) {
            final SetAside entry = iterator.next();
            if (entry.key.equals(columnKey)) {
                iterator.remove();
                final List<SortEntry<C>> result = new ArrayList<>(sort);
                result.add(Math.min(entry.index, result.size()), new SortEntry<>(column, entry.ascending));
                return result;
            }
        }
        return sort;
    }

    /// @return Whether entries are set aside, waiting for their columns to be added again.
    public boolean isPending() {
        return !setAside.isEmpty();
    }

    /// Forgets the entries no column took back, once the columns are made.
    public void finish() {
        setAside.clear();
    }


    // --------------------------------------------------------------------------------


    // An entry taken out of the sort with its column, and where it was
    private record SetAside(String key, int index, boolean ascending) {

    }
}
