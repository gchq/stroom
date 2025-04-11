package stroom.query.client.presenter;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.IncludeExcludeFilter;
import stroom.query.api.v2.Sort;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ColumnHeaderHtmlUtil {

    private ColumnHeaderHtmlUtil() {
        // Utility class.
    }

    public static SafeHtml getSafeHtml(final Column column) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        sb.appendHtmlConstant("<div class=\"column-top\">");

        // Output name.
        sb.appendHtmlConstant("<div class=\"column-label\">");
        sb.appendEscaped(column.getName());
        sb.appendHtmlConstant("</div>");

        // Show group icon.
        if (column.getGroup() != null) {
            // Show group icon.
            sb.append(getSafeHtml(SvgImage.FIELDS_GROUP));

            // Show group depth.
            sb.append(SafeHtmlUtils
                    .fromTrustedString("<div class=\"column-sortOrder\">" +
                                       (column.getGroup() + 1) +
                                       "</div>"));
        }

        // Add sort icon.
        if (column.getSort() != null) {
            if (Sort.SortDirection.ASCENDING == column.getSort().getDirection()) {
                sb.append(getSafeHtml(SvgImage.FIELDS_SORTAZ));
            } else {
                sb.append(getSafeHtml(SvgImage.FIELDS_SORTZA));
            }

            // Add sort order.
            sb.append(SafeHtmlUtils
                    .fromTrustedString("<div class=\"column-sortOrder\">" +
                                       (column.getSort().getOrder() + 1) +
                                       "</div>"));
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

        return sb.toSafeHtml();
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
