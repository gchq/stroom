package stroom.query.client.presenter;

import stroom.query.api.Column;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.Sort;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

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

        sb.appendHtmlConstant("</div>");
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
