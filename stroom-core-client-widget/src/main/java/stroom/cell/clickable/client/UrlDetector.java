package stroom.cell.clickable.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;

/**
 * This class is used to detect table cell values that contain URL's to be turned into hyperlinks.
 *
 * [:title](http://some-url/:id)
 *
 * \[([^]]+)]\(([^)]+)\)
 */
public final class UrlDetector {
    private UrlDetector() {

    }

    public static Hyperlink detect(final String value) {
        Hyperlink result = null;

        if (value.startsWith("[") && value.endsWith(")")) {
            final int dividerPosition = value.indexOf("](");

            if (dividerPosition > 0) {
                final String title = value.substring(1, dividerPosition);
                final String href = value.substring(dividerPosition + 2, value.length() - 1);

                result = new HyperlinkBuilder()
                        .title(title)
                        .href(href)
                        .build();
            }
        }

        return result;
    }

    public interface DetectedUrlTemplate extends SafeHtmlTemplates {
        @Template("<a target='_blank' href=\"{1}\">{0}</a>")
        SafeHtml messageWithLink(SafeHtml title, SafeUri href);
    }

    private static DetectedUrlTemplate TEMPLATE;

    public static class Hyperlink  {
        private String title;
        private String href;

        public Hyperlink() {
        }

        public String getTitle() {
            return title;
        }

        public String getHref() {
            return href;
        }

        public SafeHtml getSafeHtml() {
            if (null == TEMPLATE) {
                // Lazy create to prevent tests from failing
                TEMPLATE = GWT.create(DetectedUrlTemplate.class);
            }

            SafeUri href = UriUtils.fromString(this.href);
            SafeHtml title = new SafeHtmlBuilder()
                    .appendHtmlConstant(this.title)
                    .toSafeHtml();

            return TEMPLATE.messageWithLink(title, href);
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
            return result;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Hyperlink{");
            sb.append("title='").append(title).append('\'');
            sb.append(", href='").append(href).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

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

        public Hyperlink build() {
            return instance;
        }
    }
}
