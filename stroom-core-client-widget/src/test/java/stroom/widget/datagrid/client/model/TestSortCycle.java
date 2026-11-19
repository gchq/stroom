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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSortCycle {

    @Test
    void testThreeStateCycleAscendingDefault() {
        List<SortEntry<String>> list = Collections.emptyList();

        list = SortCycle.next(list, "a", true, false);
        assertThat(list).containsExactly(asc("a"));

        list = SortCycle.next(list, "a", true, false);
        assertThat(list).containsExactly(desc("a"));

        list = SortCycle.next(list, "a", true, false);
        assertThat(list).isEmpty();
    }

    @Test
    void testThreeStateCycleDescendingDefault() {
        List<SortEntry<String>> list = Collections.emptyList();

        list = SortCycle.next(list, "a", false, false);
        assertThat(list).containsExactly(desc("a"));

        list = SortCycle.next(list, "a", false, false);
        assertThat(list).containsExactly(asc("a"));

        list = SortCycle.next(list, "a", false, false);
        assertThat(list).isEmpty();
    }

    @Test
    void testPlainClickReplacesList() {
        final List<SortEntry<String>> list = Arrays.asList(asc("a"), desc("b"));

        assertThat(SortCycle.next(list, "c", true, false)).containsExactly(asc("c"));
        // A plain click on an already sorted column also drops the others.
        assertThat(SortCycle.next(list, "b", true, false)).isEmpty();
        assertThat(SortCycle.next(list, "a", true, false)).containsExactly(desc("a"));
    }

    @Test
    void testCtrlClickBuildsMultiSort() {
        List<SortEntry<String>> list = Collections.emptyList();

        list = SortCycle.next(list, "a", true, true);
        list = SortCycle.next(list, "b", true, true);
        assertThat(list).containsExactly(asc("a"), asc("b"));

        // Reversing "a" moves it to the end, as GWT does.
        list = SortCycle.next(list, "a", true, true);
        assertThat(list).containsExactly(asc("b"), desc("a"));

        // A third Ctrl click removes just that column.
        list = SortCycle.next(list, "a", true, true);
        assertThat(list).containsExactly(asc("b"));
    }

    @Test
    void testOrDefaultRestoresDefaultWhenEmpty() {
        final List<SortEntry<String>> defaultSort = Collections.singletonList(desc("created"));

        assertThat(SortCycle.orDefault(Collections.emptyList(), defaultSort)).containsExactly(desc("created"));
        assertThat(SortCycle.orDefault(Collections.singletonList(asc("a")), defaultSort))
                .containsExactly(asc("a"));
        assertThat(SortCycle.orDefault(Collections.<SortEntry<String>>emptyList(), Collections.emptyList()))
                .isEmpty();
    }

    @Test
    void testSortOrder() {
        final List<SortEntry<String>> list = Arrays.asList(asc("a"), desc("b"));

        assertThat(SortCycle.sortOrder(list, "a")).isEqualTo(1);
        assertThat(SortCycle.sortOrder(list, "b")).isEqualTo(2);
        assertThat(SortCycle.sortOrder(list, "c")).isZero();
    }

    private static SortEntry<String> asc(final String column) {
        return new SortEntry<>(column, true);
    }

    private static SortEntry<String> desc(final String column) {
        return new SortEntry<>(column, false);
    }
}
