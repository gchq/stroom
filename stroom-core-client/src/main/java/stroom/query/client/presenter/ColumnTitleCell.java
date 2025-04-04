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
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.Sort;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.SortIcon;

public class ColumnTitleCell extends AbstractCell<Column> {

    @Override
    public void render(final Context context,
                       final Column column,
                       final SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<div class=\"column-top\">");

        // Output name.
        sb.appendHtmlConstant("<div class=\"column-label\">");
        sb.appendEscaped(column.getName());
        sb.appendHtmlConstant("</div>");

        // Show group icon.
        if (column.getGroup() != null) {
            SortIcon.append(sb,
                    SvgImage.FIELDS_GROUP,
                    "Group Level " + (column.getGroup() + 1),
                    column.getGroup() + 1);
        }

        // Add sort icon.
        if (column.getSort() != null) {
            SortIcon.append(sb,
                    Sort.SortDirection.ASCENDING == column.getSort().getDirection(),
                    column.getSort().getOrder() + 1);
        }

        // Add filter icon.
        final IncludeExcludeFilter filter = column.getFilter();
        if (filter != null) {
            if ((filter.getIncludes() != null && filter.getIncludes().trim().length() > 0) ||
                (filter.getExcludes() != null && filter.getExcludes().trim().length() > 0)) {
                sb.append(getSafeHtml(SvgImage.FIELDS_FILTER));
            }
        }

        // Add value filter button.
        String className = "svgIcon column-valueFilterIcon";
        if (column.getColumnValueSelection() != null && column.getColumnValueSelection().isEnabled()) {
            className += " icon-colour__blue";
        }
        sb.appendHtmlConstant("<div class=\"column-valueFilter\">");
        sb.append(SvgImageUtil.toSafeHtml(SvgImage.VALUE_FILTER, className));
        sb.appendHtmlConstant("</div>");

        sb.appendHtmlConstant("</div>");
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
