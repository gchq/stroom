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

package stroom.query.client.presenter;

import stroom.dashboard.client.table.FilterCell;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.query.api.Column;

import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.HasCell;

import java.util.ArrayList;
import java.util.List;

public class ColumnHeaderCell extends CompositeCell<Column> {

    public ColumnHeaderCell(final List<HasCell<Column, ?>> cells) {
        super(cells);
    }

    public static ColumnHeaderCell create(final FilterCellManager filterCellManager) {
        final ColumnTitleCell columnTitleCell = new ColumnTitleCell();
        final com.google.gwt.user.cellview.client.Column<Column, Column> title =
                new com.google.gwt.user.cellview.client.Column<Column, Column>(columnTitleCell) {
                    @Override
                    public Column getValue(final Column column) {
                        return column;
                    }
                };

        final FilterCell filterCell = new FilterCell(filterCellManager);
        final com.google.gwt.user.cellview.client.Column<Column, Column> filterInput =
                new com.google.gwt.user.cellview.client.Column<Column, Column>(filterCell) {
                    @Override
                    public Column getValue(final Column column) {
                        return column;
                    }
                };
        final List<HasCell<Column, ?>> list = new ArrayList<>();
        list.add(title);
        list.add(filterInput);

        return new ColumnHeaderCell(list);
    }
}
