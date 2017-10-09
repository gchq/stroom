package stroom.cell.clickable.client;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.io.Serializable;

/**
 * Table Cell contents that are just SafeHtml but clickable to visit a URL
 */
public class ClickableSafeHtml implements Serializable {
    private final SafeHtml safeHtml;
    private final Hyperlink hyperlink;

    public ClickableSafeHtml(final SafeHtml safeHtml,
                             final Hyperlink hyperlink) {
        this.safeHtml = safeHtml;
        this.hyperlink = hyperlink;
    }

    public SafeHtml getSafeHtml() {
        return safeHtml;
    }

    public Hyperlink getHyperlink() {
        return hyperlink;
    }

    public static class Builder {
        private SafeHtml safeHtml;
        private Hyperlink hyperlink;

        public Builder() {

        }

        public Builder safeHtml(final SafeHtml safeHtml) {
            this.safeHtml = safeHtml;
            return this;
        }

        public Builder hyperlink(final Hyperlink hyperlink) {
            this.hyperlink = hyperlink;
            return this;
        }

        public ClickableSafeHtml build() {
            return new ClickableSafeHtml(this.safeHtml, this.hyperlink);
        }
    }

    public static Builder safeHtml(final SafeHtml safeHtml) {
        return new Builder().safeHtml(safeHtml);
    }
}
