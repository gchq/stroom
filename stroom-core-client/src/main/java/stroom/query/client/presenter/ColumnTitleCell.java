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

import stroom.query.api.Column;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ColumnTitleCell extends AbstractCell<Column> {

    @Override
    public void render(final Context context,
                       final Column column,
                       final SafeHtmlBuilder sb) {
        final HtmlBuilder hb = new HtmlBuilder(sb);
        ColumnHeaderHtmlUtil.write(column, hb);

        // Add value filter button.
        hb.div(div -> {
            String className = "svgIcon column-valueFilterIcon";
            if (column.getColumnValueSelection() != null && column.getColumnValueSelection().isEnabled()) {
                className += " icon-colour__blue";
            }
            div.append(SvgImageUtil.toSafeHtml(SvgImage.VALUE_FILTER, className));
        }, Attribute.className("column-valueFilter"));
    }
}
