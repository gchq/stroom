/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

/// Shared cell markup for the floor map's data grids.
///
/// Grid cells are single `nowrap` lines that ellipsise when the column is
/// too narrow (see `ui/css/celltable/DataGrid.css`), so detail that will not
/// fit goes in a `title` tooltip instead of wrapping. Both the tracking
/// panel's Area column and the Groups panel's columns need that, so the markup
/// lives here rather than being written twice.
public final class FloorMapCellHtml {

    private FloorMapCellHtml() {
        // Utility class.
    }

    /// Cell text wrapped in a span carrying a `title` tooltip.
    ///
    /// Both are escaped — entity ids, area names and group names are
    /// document/query data.
    ///
    /// @param text    the visible cell text; `null` renders empty
    /// @param tooltip the hover detail; `null` renders no tooltip text
    /// @return the cell markup
    public static SafeHtml cell(final String text, final String tooltip) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span title=\"");
        builder.appendEscaped(tooltip != null ? tooltip : "");
        builder.appendHtmlConstant("\">");
        builder.appendEscaped(text != null ? text : "");
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    /// Cell text preceded by a swatch, both inside one tooltipped span — the
    /// Groups panel's Name column, where the swatch shows the group's highlight
    /// colour.
    ///
    /// @param swatch  markup for the leading graphic (already safe)
    /// @param text    the visible cell text; `null` renders empty
    /// @param tooltip the hover detail; `null` renders no tooltip text
    /// @return the cell markup
    public static SafeHtml cellWithSwatch(final SafeHtml swatch,
                                          final String text,
                                          final String tooltip) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span class=\"floormap-cell-with-swatch\" title=\"");
        builder.appendEscaped(tooltip != null ? tooltip : "");
        builder.appendHtmlConstant("\">");
        if (swatch != null) {
            builder.appendHtmlConstant("<span class=\"floormap-cell-swatch\">");
            builder.append(swatch);
            builder.appendHtmlConstant("</span>");
        }
        builder.appendEscaped(text != null ? text : "");
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }
}
