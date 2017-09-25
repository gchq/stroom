package stroom.cell.clickable.client;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.io.Serializable;

/**
 * Table Cell contents that are just SafeHtml but clickable to visit a URL
 */
public class ClickableSafeHtml implements Serializable {
    private final SafeHtml safeHtml;
    private final UrlDetector.Hyperlink url;

    public ClickableSafeHtml(final SafeHtml safeHtml,
                             final UrlDetector.Hyperlink url) {
        this.safeHtml = safeHtml;
        this.url = url;
    }

    public SafeHtml getSafeHtml() {
        return safeHtml;
    }

    public UrlDetector.Hyperlink getUrl() {
        return url;
    }

    public static class Builder {
        private SafeHtml safeHtml;
        private UrlDetector.Hyperlink url;

        public Builder() {

        }

        public Builder safeHtml(final SafeHtml safeHtml) {
            this.safeHtml = safeHtml;
            return this;
        }

        public Builder url(final UrlDetector.Hyperlink url) {
            this.url = url;
            return this;
        }

        public ClickableSafeHtml build() {
            return new ClickableSafeHtml(this.safeHtml, this.url);
        }
    }

    public static Builder safeHtml(final SafeHtml safeHtml) {
        return new Builder().safeHtml(safeHtml);
    }
}
