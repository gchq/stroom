/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.datagrid.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.safehtml.shared.SafeHtml;

/// The sort direction icon with the column's sort position, as shown in a sorted column's header
/// (design requirement R-H3). The markup matches [SortIcon]'s so the same CSS applies.
final class DataGridSortIcon {

    static final String SORT_ASCENDING = "Sort Ascending";
    static final String SORT_DESCENDING = "Sort Descending";

    private DataGridSortIcon() {
        // Utility class.
    }

    /// @param ascending Whether the column is sorted ascending.
    /// @param order     The column's 1-based position in the sort list.
    /// @return The icon markup.
    static SafeHtml render(final boolean ascending, final int order) {
        final SvgImage image = ascending
                ? SvgImage.FIELDS_SORT_ASCENDING
                : SvgImage.FIELDS_SORT_DESCENDING;
        final String title = ascending
                ? SORT_ASCENDING
                : SORT_DESCENDING;
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            div.append(SvgImageUtil.toSafeHtml(image, "svgIcon"));
            div.div(c -> c.append(order), Attribute.className("column-header__sort-order"));
        }, Attribute.className("column-header__sort"), Attribute.title(title));
        return hb.toSafeHtml();
    }
}
