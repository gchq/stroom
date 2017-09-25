package stroom.cell.clickable.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;

import java.util.logging.Logger;

/**
 * This class is used to detect table cell values that contain URL's to be turned into hyperlinks.
 *
 * [:title](http://some-url/:id){:hyperlinkTarget}
 *
 */
public class Hyperlink {

    private static final Logger LOGGER = Logger.getLogger(Hyperlink.class.getName());

    private String title;
    private String href;
    private HyperlinkTarget target;

    public static Hyperlink detect(final String value) {
        Hyperlink result = null;

        if (value.startsWith("[") && value.endsWith("}")) {
            final int firstDividerPosition = value.indexOf("](");
            final int secondDividerPosition = value.indexOf("){");

            if ((firstDividerPosition > 0) && (secondDividerPosition > 0)) {
                final String title = value.substring(1, firstDividerPosition);
                final String href = value.substring(firstDividerPosition + 2, secondDividerPosition);
                final String openTypeStr = value.substring(secondDividerPosition + 2, value.length() - 1);

                try {
                    final HyperlinkTarget target = HyperlinkTarget.valueOf(openTypeStr);

                    result = new Hyperlink.HyperlinkBuilder()
                            .title(title)
                            .href(href)
                            .target(target)
                            .build();
                } catch (Exception e) {
                    LOGGER.warning("Could not parse open type value of " + openTypeStr);
                }
            }
        }

        return result;
    }

    public Hyperlink() {
    }

    public String getTitle() {
        return title;
    }

    public String getHref() {
        return href;
    }

    public HyperlinkTarget getTarget() {
        return target;
    }

    public SafeHtml toSafeHtml() {
        if (null == TEMPLATE) {
            // Lazy create to prevent tests from failing
            TEMPLATE = GWT.create(OpenUrlNewBrowserTabTemplate.class);
        }

        switch (this.target) {
            case STROOM_TAB:
            case DIALOG:
                final SafeHtmlBuilder sb = new SafeHtmlBuilder();
                sb.appendHtmlConstant("<u>");
                sb.appendEscaped(title);
                sb.appendHtmlConstant("</u>");
                return sb.toSafeHtml();
            case BROWSER_TAB:
                SafeUri href = UriUtils.fromString(this.href);
                SafeHtml title = new SafeHtmlBuilder()
                        .appendHtmlConstant(this.title)
                        .toSafeHtml();
                return TEMPLATE.hyperlink(title, href);
            default:
                return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Hyperlink hyperlink = (Hyperlink) o;

        if (title != null ? !title.equals(hyperlink.title) : hyperlink.title != null) return false;
        return href != null ? href.equals(hyperlink.href) : hyperlink.href == null;
    }

    @Override
    public int hashCode() {
        int result = title != null ? title.hashCode() : 0;
        result = 31 * result + (href != null ? href.hashCode() : 0);
        result = 31 * result + (target != null ? target.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Hyperlink{");
        sb.append("title='").append(title).append('\'');
        sb.append(", href='").append(href).append('\'');
        sb.append(", hyperlinkTarget='").append(target).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public interface OpenUrlNewBrowserTabTemplate extends SafeHtmlTemplates {
        @Template("<a target='_blank' href=\"{1}\">{0}</a>")
        SafeHtml hyperlink(SafeHtml title, SafeUri href);
    }

    private static OpenUrlNewBrowserTabTemplate TEMPLATE;

    public static class HyperlinkBuilder {
        private final Hyperlink instance;

        public HyperlinkBuilder() {
            this.instance = new Hyperlink();
        }

        public HyperlinkBuilder title(final String title) {
            this.instance.title = title;
            return this;
        }

        public HyperlinkBuilder href(final String href) {
            this.instance.href = href;
            return this;
        }

        public HyperlinkBuilder target(final HyperlinkTarget target) {
            this.instance.target = target;
            return this;
        }

        public Hyperlink build() {
            return instance;
        }
    }
}
