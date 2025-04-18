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

import stroom.query.api.Column;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ColumnTitleCell extends AbstractCell<Column> {

    @Override
    public void render(final Context context,
                       final Column column,
                       final SafeHtmlBuilder sb) {
        ColumnHeaderHtmlUtil.write(column, sb);

        // Add value filter button.
        String className = "svgIcon column-valueFilterIcon";
        if (column.getColumnValueSelection() != null && column.getColumnValueSelection().isEnabled()) {
            className += " icon-colour__blue";
        }
        sb.appendHtmlConstant("<div class=\"column-valueFilter\">");
        sb.append(SvgImageUtil.toSafeHtml(SvgImage.VALUE_FILTER, className));
        sb.appendHtmlConstant("</div>");
    }
}
