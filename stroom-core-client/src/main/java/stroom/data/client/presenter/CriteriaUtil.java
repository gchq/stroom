/*
 * Copyright 2022 Crown Copyright
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

package stroom.data.client.presenter;

import stroom.data.grid.client.OrderByColumn;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;

public final class CriteriaUtil {

    private CriteriaUtil() {
    }

    public static void setRange(final BaseCriteria criteria,
                                final Range range) {
        criteria.setPageRequest(createPageRequest(range));
    }

    /**
     * Apply the grid's sorts to {@code criteria}, <b>leaving any sorts the criteria already carries
     * untouched when the grid has none of its own</b>.
     * <p>
     * The grid contributes nothing until the user clicks a column heading, so replacing the list
     * unconditionally discarded the default sort several presenters seed in their constructor — the
     * grid then opened in whatever order the server happened to return. Presenters that seed nothing
     * are unaffected: an empty list replacing an empty list.
     */
    public static void setSortList(final BaseCriteria criteria,
                                   final ColumnSortList columnSortList) {
        final List<CriteriaFieldSort> sortList = createSortList(columnSortList);
        if (!sortList.isEmpty()) {
            criteria.setSortList(sortList);
        }
    }

    public static PageRequest createPageRequest(final Range range) {
        return new PageRequest(range.getStart(), range.getLength());
    }

    public static List<CriteriaFieldSort> createSortList(final ColumnSortList columnSortList) {
        final List<CriteriaFieldSort> criteriaSortList = new ArrayList<>();
        if (columnSortList != null) {
            for (int i = 0; i < columnSortList.size(); i++) {
                final ColumnSortInfo columnSortInfo = columnSortList.get(i);
                final Column<?, ?> column = columnSortInfo.getColumn();

                if (column instanceof final OrderByColumn<?, ?> orderByColumn) {
                    final String dataStoreName = orderByColumn.getField();
                    if (dataStoreName != null) {
                        criteriaSortList.add(new CriteriaFieldSort(
                                dataStoreName,
                                !columnSortInfo.isAscending(),
                                orderByColumn.isIgnoreCase()));
                    }
                } else {
                    final String dataStoreName = column.getDataStoreName();
                    if (dataStoreName != null) {
                        criteriaSortList.add(new CriteriaFieldSort(
                                dataStoreName,
                                !columnSortInfo.isAscending(),
                                true));
                    }
                }
            }
        }
        return criteriaSortList;
    }

    public static boolean hasSortColumn(final ColumnSortList columnSortList, final String columnName) {
        for (int i = 0; i < columnSortList.size(); i++) {
            final ColumnSortInfo columnSortInfo = columnSortList.get(i);
            final Column<?, ?> column = columnSortInfo.getColumn();
            if (column instanceof final OrderByColumn<?, ?> orderByColumn) {
                final String dataStoreName = orderByColumn.getField();
                if (columnName.equals(dataStoreName)) {
                    return true;
                }
            } else {
                final String dataStoreName = column.getDataStoreName();
                if (dataStoreName != null) {
                    if (columnName.equals(dataStoreName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
