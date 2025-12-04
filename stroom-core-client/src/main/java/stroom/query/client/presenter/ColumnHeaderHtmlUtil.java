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

import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.Sort;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.SortIcon;

import java.util.Set;
import java.util.stream.Collectors;

public class ColumnHeaderHtmlUtil {

    private ColumnHeaderHtmlUtil() {
        // Utility class.
    }

    public static SafeHtml getSafeHtml(final Column column) {
        final HtmlBuilder hb = new HtmlBuilder();
        write(column, hb);
        return hb.toSafeHtml();
    }

    public static void write(final Column column,
                             final HtmlBuilder hb) {
        hb.div(div -> writeHeader(column, div),
                Attribute.className("column-top"),
                Attribute.title(getColumnInfoString(column).asString()));
    }

    public static void writeHeader(final Column column,
                                   final HtmlBuilder hb) {
        // Output name.
        hb.div(column.getName(), Attribute.className("column-label"));

        // Show group icon.
        if (column.getGroup() != null) {
            SortIcon.append(hb,
                    SvgImage.FIELDS_GROUP,
                    "Group Level " + (column.getGroup() + 1),
                    column.getGroup() + 1);
        }

        // Add sort icon.
        if (column.getSort() != null) {
            SortIcon.append(hb,
                    Sort.SortDirection.ASCENDING == column.getSort().getDirection(),
                    column.getSort().getOrder() + 1);
        }

        // Add filter icon.
        final IncludeExcludeFilter filter = column.getFilter();
        if (filter != null) {
            if ((filter.getIncludes() != null && !filter.getIncludes().trim().isEmpty()) ||
                (filter.getExcludes() != null && !filter.getExcludes().trim().isEmpty()) ||
                !filter.getIncludeDictionaries().isEmpty() || !filter.getExcludeDictionaries().isEmpty()
            ) {
                hb.append(getSafeHtml(SvgImage.FIELDS_FILTER));
            }
        }
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }

    private static SafeHtml getColumnInfoString(final Column column) {
        final HtmlBuilder hb = new HtmlBuilder();

        if (column.getFormat() != null && column.getFormat().getType() != null) {
            hb.append("Format: ").append(column.getFormat().getType().getDisplayValue()).append('\n');
        }

        hb.append("Expression: ").append(column.getExpression()).append('\n');

        if (column.getSort() != null && column.getSort().getDirection() != null) {
            hb.append("Sort: ").append(column.getSort().getDirection().getDisplayValue()).append('\n');
            hb.append("Sort Priority: ").append(column.getSort().getOrder() + 1).append('\n');
        }

        if (column.getGroup() != null) {
            hb.append("Group Level: ").append(column.getGroup() + 1).append('\n');
        }

        if (column.getColumnValueSelection() != null) {
            if (column.getColumnValueSelection().isInvert()) {
                hb.append("Selection Excluded: ");
            } else {
                hb.append("Selection Included: ");
            }
            hb.append(NullSafe
                            .stream(column.getColumnValueSelection().getValues())
                            .collect(Collectors.joining(",")))
                    .append('\n');
        }

        if (column.getFilter() != null) {
            if (column.getFilter().getIncludes() != null && !column.getFilter().getIncludes().isBlank()) {
                hb.append("Filter Includes: ").append(column.getFilter().getIncludes()).append('\n');
            }
            if (column.getFilter().getExcludes() != null && !column.getFilter().getExcludes().isBlank()) {
                hb.append("Filter Excludes: ").append(column.getFilter().getExcludes()).append('\n');
            }
            if (!column.getFilter().getIncludeDictionaries().isEmpty()) {
                final Set<String> dictionaryNames = column.getFilter().getIncludeDictionaries().stream()
                        .map(DocRef::getDisplayValue).collect(Collectors.toSet());
                hb.append("Includes Dictionaries: ").append(dictionaryNames.toString()).append("\n");
            }
            if (!column.getFilter().getExcludeDictionaries().isEmpty()) {
                final Set<String> dictionaryNames = column.getFilter().getExcludeDictionaries().stream()
                        .map(DocRef::getDisplayValue).collect(Collectors.toSet());
                hb.append("Excludes Dictionaries: ").append(dictionaryNames.toString()).append("\n");
            }
        }

        if (column.getColumnFilter() != null && column.getColumnFilter().getFilter() != null &&
            !column.getColumnFilter().getFilter().isBlank()) {
            hb.append("Column Filter: ").append(column.getColumnFilter().getFilter()).append('\n');
        }

        return hb.toSafeHtml();
    }
}
