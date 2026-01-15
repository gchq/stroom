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

import stroom.data.grid.client.HasContextMenus;
import stroom.query.api.Column;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Collections;
import java.util.List;

public class TableRowCell extends AbstractCell<TableRow> implements HasContextMenus<TableRow> {

    private final AnnotationManager annotationManager;
    private final Column column;

    public TableRowCell(final AnnotationManager annotationManager, final Column column) {
        this.annotationManager = annotationManager;
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
            return annotationManager.getMenuItems(Collections.singletonList(value));
        }
        return Collections.emptyList();
    }
}
