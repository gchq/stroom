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

/// The sort icon of a column's header, and the names and icons of its sort states, as the dashboard
/// and query tables' column menus offer them.
public final class SortIcon {

    public static final String SORT = "Sort";
    public static final String UNSORTED = "Unsorted";
    public static final String SORT_ASCENDING = "Sort Ascending";
    public static final String SORT_DESCENDING = "Sort Descending";
    public static final SvgImage SORT_ASCENDING_ICON = SvgImage.FIELDS_SORT_ASCENDING;
    public static final SvgImage SORT_DESCENDING_ICON = SvgImage.FIELDS_SORT_DESCENDING;
    public static final SvgImage SORT_NONE = SvgImage.FIELDS_SORT_NONE;

    private SortIcon() {
        // Static utility
    }

    /// Appends a column's sort icon: its direction's arrow and its place in the sort order.
    ///
    /// @param sb        The HTML to append to.
    /// @param ascending Whether the column sorts ascending.
    /// @param order     The column's place in the sort order, from 1.
    public static void append(final HtmlBuilder sb,
                              final boolean ascending,
                              final int order) {
        append(sb,
                ascending
                        ? SORT_ASCENDING_ICON
                        : SORT_DESCENDING_ICON,
                ascending
                        ? SORT_ASCENDING
                        : SORT_DESCENDING,
                order);
    }

    /// Appends a sort icon.
    ///
    /// @param sb       The HTML to append to.
    /// @param svgImage The icon.
    /// @param title    The icon's title, e.g. [#SORT_ASCENDING].
    /// @param order    The column's place in the sort order, from 1.
    public static void append(final HtmlBuilder sb,
                              final SvgImage svgImage,
                              final String title,
                              final int order) {
        sb.div(div -> {
            // Add icon.
            div.append(getSafeHtml(svgImage));
            // Add order.
            div.div(c -> c.append(order), Attribute.className("column-header__sort-order"));
        }, Attribute.className("column-header__sort"), Attribute.title(title));
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
