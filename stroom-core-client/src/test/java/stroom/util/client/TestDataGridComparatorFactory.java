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

package stroom.util.client;

import stroom.widget.datagrid.client.GridDisplay;
import stroom.widget.datagrid.client.OrderByColumn;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataGridComparatorFactory {

    private static final List<String> NAMES = List.of("B", "a", "C");

    @Test
    void testIgnoreCaseColumnSortsIgnoringCase() {
        assertThat(sorted(true, true)).containsExactly("a", "B", "C");
        assertThat(sorted(true, false)).containsExactly("C", "B", "a");
    }

    @Test
    void testCaseSensitiveColumnSortsByCase() {
        assertThat(sorted(false, true)).containsExactly("B", "C", "a");
        assertThat(sorted(false, false)).containsExactly("a", "C", "B");
    }

    @Test
    void testIgnoreCaseLeavesOtherTypesAlone() {
        final ColumnSortList sortList = new ColumnSortList();
        sortList.push(new ColumnSortInfo(column(true), true));
        final DataGridComparatorFactory<String> factory = DataGridComparatorFactory.builder(grid(sortList))
                .addField("name", String::length)
                .build();
        final List<String> rows = new ArrayList<>(List.of("ccc", "a", "bb"));
        rows.sort(factory.create());
        assertThat(rows).containsExactly("a", "bb", "ccc");
    }

    private static List<String> sorted(final boolean ignoreCase, final boolean ascending) {
        final ColumnSortList sortList = new ColumnSortList();
        sortList.push(new ColumnSortInfo(column(ignoreCase), ascending));
        final DataGridComparatorFactory<String> factory = DataGridComparatorFactory.builder(grid(sortList))
                .addField("name", name -> name)
                .build();
        final List<String> rows = new ArrayList<>(NAMES);
        rows.sort(factory.create());
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static GridDisplay<String> grid(final ColumnSortList sortList) {
        final GridDisplay<String> grid = Mockito.mock(GridDisplay.class);
        Mockito.when(grid.getColumnSortList()).thenReturn(sortList);
        return grid;
    }

    @SuppressWarnings("unchecked")
    private static OrderByColumn<String, String> column(final boolean ignoreCase) {
        final Cell<String> cell = Mockito.mock(Cell.class);
        return new OrderByColumn<String, String>(cell, "name", ignoreCase) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        };
    }
}
