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

package stroom.query.client.result.table;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.dashboard.shared.ColumnValue;
import stroom.query.api.ColumnValueSelection;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ColumnValueCell extends AbstractCell<ColumnValue> {

    private static final TickBoxCell.DefaultAppearance TICK_BOX = new TickBoxCell.DefaultAppearance();

    private final ColumnValueSelection.Builder columnValueSelection;

    public ColumnValueCell(final ColumnValueSelection.Builder columnValueSelection) {
        this.columnValueSelection = columnValueSelection;
    }

    @Override
    public void render(final Context context, final ColumnValue item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final HtmlBuilder hb = new HtmlBuilder(sb);
            hb.div(outer -> {

                // The tick box (tick-box.css), showing whether the value is chosen, as a picture: the
                // list's option says it (aria-checked), and is what is clicked to change it
                outer.append(TICK_BOX.getPicture(stateOf(columnValueSelection, item)));

                outer.div(item.getValue(), Attribute.className("column-value-cell__text"));
            }, Attribute.className("column-value-cell"));
        }
    }

    /// @param columnValueSelection The values chosen.
    /// @param item                 A value.
    /// @return Whether the value is chosen, as a tick box shows it.
    public static TickBoxState stateOf(final ColumnValueSelection.Builder columnValueSelection,
                                       final ColumnValue item) {
        final ColumnValueSelection selection = columnValueSelection.build();
        final boolean selected;
        if (selection.getValues().contains(item.getValue())) {
            selected = !selection.isInvert();
        } else {
            selected = selection.isInvert();
        }
        return selected
                ? TickBoxState.TICK
                : TickBoxState.UNTICK;
    }
}
