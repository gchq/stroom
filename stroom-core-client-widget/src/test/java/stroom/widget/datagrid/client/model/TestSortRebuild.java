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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSortRebuild {

    // A column made afresh is a new object with the same sort field: here "name#1" and "name#2"
    // both sort by "name"; a column with no '#' has no sort field
    private final SortRebuild<String> rebuild = new SortRebuild<>(column -> column.contains("#")
            ? column.substring(0, column.indexOf('#'))
            : null);

    @Test
    void aColumnMadeAfreshTakesBackItsSort() {
        // Before, the sort kept the removed column, so the new one showed no sort
        List<SortEntry<String>> sort = List.of(new SortEntry<>("name#1", true));

        sort = rebuild.remove(sort, "enabled#1");
        sort = rebuild.remove(sort, "name#1");
        assertThat(sort).isEmpty();
        assertThat(rebuild.isPending()).isTrue();

        sort = rebuild.add(sort, "enabled#2");
        sort = rebuild.add(sort, "name#2");
        assertThat(sort).containsExactly(new SortEntry<>("name#2", true));
        assertThat(rebuild.isPending()).isFalse();
    }

    @Test
    void aSortOnSeveralColumnsComesBackInOrderAndDirection() {
        List<SortEntry<String>> sort = List.of(new SortEntry<>("node#1", false), new SortEntry<>("name#1", true));

        // Removed in the grid's order, the first column first, and added again in it
        sort = rebuild.remove(sort, "name#1");
        sort = rebuild.remove(sort, "node#1");
        sort = rebuild.add(sort, "name#2");
        sort = rebuild.add(sort, "node#2");

        assertThat(sort).containsExactly(new SortEntry<>("node#2", false), new SortEntry<>("name#2", true));
    }

    @Test
    void aColumnRemovedForGoodLeavesTheSortAndIsForgotten() {
        List<SortEntry<String>> sort = List.of(new SortEntry<>("name#1", true), new SortEntry<>("node#1", true));

        sort = rebuild.remove(sort, "node#1");
        assertThat(sort).containsExactly(new SortEntry<>("name#1", true));

        rebuild.finish();
        assertThat(rebuild.isPending()).isFalse();
        // A column with that sort field added later is a new column, not a rebuild
        assertThat(rebuild.add(sort, "node#2")).containsExactly(new SortEntry<>("name#1", true));
    }

    @Test
    void aColumnWithNoSortFieldIsTakenOutButNotKept() {
        List<SortEntry<String>> sort = List.of(new SortEntry<>("computed", true));

        sort = rebuild.remove(sort, "computed");

        assertThat(sort).isEmpty();
        assertThat(rebuild.isPending()).isFalse();
    }

    @Test
    void anUnsortedColumnChangesNothing() {
        final List<SortEntry<String>> sort = List.of(new SortEntry<>("name#1", true));

        assertThat(rebuild.remove(sort, "enabled#1")).isEqualTo(sort);
        assertThat(rebuild.isPending()).isFalse();
        assertThat(rebuild.add(sort, "enabled#2")).isEqualTo(sort);
    }
}
