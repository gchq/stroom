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

package stroom.dashboard.client.table;

import stroom.dashboard.shared.ColumnValue;
import stroom.query.api.ColumnValueSelection;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ColumnValueCell extends AbstractCell<ColumnValue> {

    private final ColumnValueSelection.Builder columnValueSelection;

    public ColumnValueCell(final ColumnValueSelection.Builder columnValueSelection) {
        this.columnValueSelection = columnValueSelection;
    }

    @Override
    public void render(final Context context, final ColumnValue item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final HtmlBuilder hb = new HtmlBuilder(sb);
            final ColumnValueSelection selection = columnValueSelection.build();

            final boolean selected;
            if (selection.getValues().contains(item.getValue())) {
                selected = !selection.isInvert();
            } else {
                selected = selection.isInvert();
            }

            hb.div(outer -> {

                final SafeHtml safeHtml;
                if (selected) {
                    safeHtml = SvgImageUtil.toSafeHtml(
                            "Ticked",
                            SvgImage.TICK,
                            "tickBox",
                            "tickBox-noBorder",
                            "tickBox-tick");
                    outer.append(safeHtml);

                } else {
                    outer.div("",
                            Attribute.title("Not Ticked"),
                            Attribute.className("tickBox tickBox-noBorder tickBox-untick"));
                }

                outer.div(item.getValue(), Attribute.className("columnValueCell-text"));
            }, Attribute.className("columnValueCell"));
        }
    }
}
