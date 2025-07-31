package stroom.query.client.presenter;

import stroom.query.api.Column;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.Sort;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.SortIcon;

public class ColumnHeaderHtmlUtil {

    private ColumnHeaderHtmlUtil() {
        // Utility class.
    }

    public static SafeHtml getSafeHtml(final Column column) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        write(column, sb);
        return sb.toSafeHtml();
    }

    public static void write(final Column column,
                             final SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<div class=\"column-top\" title=\"" + getColumnInfoString(column) + "\">");

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

        sb.appendHtmlConstant("</div>");
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }

    private static String getColumnInfoString(final Column column) {
        final StringBuilder sb = new StringBuilder();

        if (column.getFormat() != null && column.getFormat().getType() != null) {
            sb.append("Format: ").append(column.getFormat().getType().getDisplayValue()).append('\n');
        }

        sb.append("Expression: ").append(column.getExpression()).append('\n');

        if (column.getSort() != null && column.getSort().getDirection() != null) {
            sb.append("Sort: ").append(column.getSort().getDirection().getDisplayValue()).append('\n');
            sb.append("Sort Priority: ").append(column.getSort().getOrder() + 1).append('\n');
        }

        if (column.getGroup() != null) {
            sb.append("Group Level: ").append(column.getGroup() + 1).append('\n');
        }

        if (column.getColumnValueSelection() != null) {
            if (column.getColumnValueSelection().isInvert()) {
                sb.append("Selection Excluded: ");
            } else {
                sb.append("Selection Included: ");
            }
            sb.append(column.getColumnValueSelection().getValues()).append('\n');
        }

        if (column.getFilter() != null) {
            if (column.getFilter().getIncludes() != null && !column.getFilter().getIncludes().isBlank()) {
                sb.append("Filter Includes: ").append(column.getFilter().getIncludes()).append('\n');
            }
            if (column.getFilter().getExcludes() != null && !column.getFilter().getExcludes().isBlank()) {
                sb.append("Filter Excludes: ").append(column.getFilter().getExcludes()).append('\n');
            }
        }

        if (column.getColumnFilter() != null && column.getColumnFilter().getFilter() != null &&
            !column.getColumnFilter().getFilter().isBlank()) {
            sb.append("Column Filter: ").append(column.getColumnFilter().getFilter()).append('\n');
        }

        return sb.toString();
    }
}
