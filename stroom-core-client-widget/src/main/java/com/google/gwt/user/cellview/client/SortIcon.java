package com.google.gwt.user.cellview.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.builder.shared.DivBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;

public class SortIcon {

    public static final String SORT = "Sort";
    public static final String UNSORTED = "Unsorted";
    public static final String SORT_ASCENDING = "Sort Ascending";
    public static final String SORT_DESCENDING = "Sort Descending";
    public static final SvgImage SORT_ASCENDING_ICON = SvgImage.FIELDS_SORT_ASCENDING;
    public static final SvgImage SORT_DESCENDING_ICON = SvgImage.FIELDS_SORT_DESCENDING;
    public static final SvgImage SORT_NONE = SvgImage.FIELDS_SORT_NONE;

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

    public static void append(final HtmlBuilder sb,
                              final SvgImage svgImage,
                              final String title,
                              final int order) {
        sb.div(div -> {
            // Add icon.
            div.append(getSafeHtml(svgImage));
            // Add order.
            div.div(c -> c.append(order), Attribute.className("column-sortOrder"));
        }, Attribute.className("column-sortIcon"), Attribute.title(title));
    }

    public static void append(final DivBuilder outerDiv,
                              final boolean ascending,
                              final int order) {
        final DivBuilder imageHolder = outerDiv.startDiv();
        imageHolder.className("column-sortIcon");

        final HtmlBuilder hb = new HtmlBuilder();
        if (ascending) {
            imageHolder.title(SORT_ASCENDING);
            hb.append(getSafeHtml(SORT_ASCENDING_ICON));
        } else {
            imageHolder.title(SORT_DESCENDING);
            hb.append(getSafeHtml(SORT_DESCENDING_ICON));
        }
        // Add sort order.
        hb.div(c -> c.append(order), Attribute.className("column-sortOrder"));
        imageHolder.html(hb.toSafeHtml());

        imageHolder.endDiv();
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
