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

import stroom.data.grid.client.HasContextMenus;
import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.List;

public class HasContextMenusCell<T_CELL> extends AbstractCell<T_CELL> implements HasContextMenus<T_CELL> {

    private final HasContextMenus<T_CELL> hasContextMenus;

    public HasContextMenusCell(final HasContextMenus<T_CELL> hasContextMenus) {
        this.hasContextMenus = hasContextMenus;
    }

    @Override
    public void render(final Context context, final T_CELL value, final SafeHtmlBuilder sb) {
        if (value != null) {
            if (value instanceof final SafeHtml safeHtml) {
                sb.append(safeHtml);
            } else {
                sb.append(SafeHtmlUtils.fromString(value.toString()));
            }
        }
    }

    @Override
    public List<Item> getContextMenuItems(final Context context, final T_CELL value) {
        return hasContextMenus.getContextMenuItems(context, value);
    }
}
