/*
 * Copyright 2025 Crown Copyright
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

import stroom.query.api.Column;
import stroom.widget.datagrid.client.HasContextMenus;
import stroom.widget.menu.client.Item;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/// A query result table's cell: a row's value for a column, with the context menu items its owner
/// gives for the row (e.g. annotating it).
public class TableRowCell extends AbstractCell<TableRow> implements HasContextMenus<TableRow> {

    private final Function<List<TableRow>, List<Item>> menuItems;
    private final Column column;

    /// @param menuItems Gives the context menu items for rows, e.g. the table's annotation menu's.
    /// @param column    The column whose value the cell shows.
    public TableRowCell(final Function<List<TableRow>, List<Item>> menuItems, final Column column) {
        this.menuItems = menuItems;
        this.column = column;
    }

    @Override
    public void render(final Context context, final TableRow value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtil.NBSP);
        } else {
            sb.append(value.getValue(column.getId()));
        }
    }

    @Override
    public List<Item> getContextMenuItems(final Context context, final TableRow value) {
        if (value != null) {
            return menuItems.apply(Collections.singletonList(value));
        }
        return Collections.emptyList();
    }
}
