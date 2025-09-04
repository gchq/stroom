package stroom.data.grid.client;

import stroom.util.shared.NullSafe;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
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
        this.headingText = NullSafe.requireNonNullElse(headingText, SafeHtmlUtils.EMPTY_SAFE_HTML);
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

        final boolean hasToolTip = NullSafe.isNonBlankString(toolTip);
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

        if (hasToolTip) {
            final SafeHtml headingDiv = SafeHtmlUtil.getTemplate().divWithTitle(toolTip, headingText);
            header = new SafeHtmlHeader(headingDiv);
        } else {
            header = new SafeHtmlHeader(headingText);
        }

        // Apply a class to the header itself
        NullSafe.consume(headingStyle, header::setHeaderStyleNames);
        return header;
    }
}
