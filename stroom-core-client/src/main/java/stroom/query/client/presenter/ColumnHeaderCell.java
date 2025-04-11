/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.client.presenter;

import stroom.dashboard.client.table.FilterCell;
import stroom.dashboard.client.table.HasValueFilter;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ColumnFilter;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.ArrayList;
import java.util.List;

public class ColumnHeaderCell extends CompositeCell<Column> {

    public ColumnHeaderCell(final List<HasCell<Column, ?>> cells) {
        super(cells);
    }

    public static ColumnHeaderCell create(final HasValueFilter hasValueFilter) {
        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> name =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        return ColumnHeaderHtmlUtil.getSafeHtml(column);
                    }
                };

        final FilterCell filterCell = new FilterCell();
        final com.google.gwt.user.cellview.client.Column<Column, String> filterInput =
                new com.google.gwt.user.cellview.client.Column<Column, String>(filterCell) {
                    @Override
                    public String getValue(final Column column) {
                        return GwtNullSafe.get(column.getColumnFilter(), ColumnFilter::getFilter);
                    }
                };
        filterInput.setFieldUpdater(new FieldUpdater<Column, String>() {
            @Override
            public void update(final int index, final Column column, final String value) {
                hasValueFilter.setValueFilter(column, value);
            }
        });

        final List<HasCell<Column, ?>> list = new ArrayList<>();
        list.add(name);
        list.add(filterInput);

        return new ColumnHeaderCell(list);
    }
}
