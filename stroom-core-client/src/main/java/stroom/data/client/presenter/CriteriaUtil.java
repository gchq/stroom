/*
 * Copyright 2016-2025 Crown Copyright
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

    public static void setSortList(final BaseCriteria criteria,
                                   final ColumnSortList columnSortList) {
        criteria.setSortList(createSortList(columnSortList));
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

                if (column instanceof OrderByColumn<?, ?>) {
                    final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) column;
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
}
