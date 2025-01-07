package stroom.data.grid.client;

import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;

public class HeadingBuilder {

    private HeadingAlignment headingAlignment = null;
    private SafeHtml headingText = SafeHtmlUtils.EMPTY_SAFE_HTML;
    private String toolTip;

    public HeadingBuilder(final String headingText) {
        this.headingText = SafeHtmlUtil.getSafeHtml(headingText);
    }

    public HeadingBuilder headingText(final String headingText) {
        this.headingText = SafeHtmlUtil.getSafeHtml(headingText);
        return this;
    }

    public HeadingBuilder headingText(final SafeHtml headingText) {
        this.headingText = GwtNullSafe.requireNonNullElse(headingText, SafeHtmlUtils.EMPTY_SAFE_HTML);
        return this;
    }

    public HeadingBuilder leftAligned() {
        this.headingAlignment = HeadingAlignment.LEFT;
        return this;
    }

    public HeadingBuilder centerAligned() {
        this.headingAlignment = HeadingAlignment.CENTER;
        return this;
    }

    public HeadingBuilder rightAligned() {
        this.headingAlignment = HeadingAlignment.RIGHT;
        return this;
    }

    public HeadingBuilder withToolTip(final String toolTip) {
        this.toolTip = toolTip;
        return this;
    }

    public Header<SafeHtml> build() {

        final boolean hasToolTip = !GwtNullSafe.isBlankString(toolTip);
        final boolean hasAlignment = headingAlignment != null
                                     && headingAlignment != HeadingAlignment.LEFT;
        final Header<SafeHtml> header;
        String headingStyle = null;
        if (hasAlignment) {
            if (HeadingAlignment.CENTER == headingAlignment) {
                headingStyle = "center-align";
            } else if (HeadingAlignment.RIGHT == headingAlignment) {
                headingStyle = "right-align";
            }
        }

//            if (hasToolTip || hasAlignment) {
        if (hasToolTip) {

            final SafeHtmlBuilder builder = new SafeHtmlBuilder()
                    .appendHtmlConstant("<div");
//                if (hasToolTip) {
            builder.appendHtmlConstant(" title=\"")
                    .appendEscaped(toolTip)
                    .appendHtmlConstant("\"");
//                }
//                if (hasAlignment) {
//                    if (HeadingAlignment.CENTER == headingAlignment) {
//                        builder.appendHtmlConstant(" style=\"text-align: center;\"");
//                        headingStyle = "center-align";
//                    } else if (HeadingAlignment.RIGHT == headingAlignment) {
//                        builder.appendHtmlConstant(" style=\"text-align: right;\"");
//                        headingStyle = "right-align";
//                    }
//                }

            builder.appendHtmlConstant(">")
                    .append(headingText);
//                if (GwtNullSafe.isBlankString(headingText)) {
//                    builder.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
//                } else {
//                    builder.appendEscaped(headingText);
//                }

            final SafeHtml safeHtml = builder
                    .appendHtmlConstant("</div>")
                    .toSafeHtml();
            header = new SafeHtmlHeader(safeHtml);
        } else {
            header = new SafeHtmlHeader(headingText);
        }

        // Apply a class to the header itself
        GwtNullSafe.consume(headingStyle, header::setHeaderStyleNames);
        return header;
    }
}