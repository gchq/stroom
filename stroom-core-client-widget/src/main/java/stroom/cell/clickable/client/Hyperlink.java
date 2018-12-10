package stroom.cell.clickable.client;

import java.util.Objects;

/**
 * This class is used to detect table cell values that contain URL's to be turned into hyperlinks.
 * <p>
 * [:title](http://some-url/:id){:hyperlinkTarget}
 */
public class Hyperlink {
    private String title;
    private String href;
    private String type;

    public static Hyperlink detect(final String value) {
        Hyperlink result = null;

        String title = null;
        String href = null;
        String type = null;

        title = extract(value, 0, "[]");
        if (title != null) {
            href = extract(value, title.length() + 2, "()");
            if (href != null) {
                type = extract(value, title.length() + href.length() + 4, "{}");
            }
        }

        if (title != null && href != null) {
            result = new Hyperlink.HyperlinkBuilder()
                    .title(title)
                    .href(href)
                    .type(type)
                    .build();
        }

        return result;
    }

    private static String extract(final String value, final int pos, final String brakets) {
        int start = value.indexOf(brakets.charAt(0), pos);
        if (start == pos) {
            int end = value.indexOf(brakets.charAt(1), start);
            if (end != -1) {
                return value.substring(start + 1, end);
            }
        }
        return null;
    }

    public Hyperlink() {
    }

    public String getTitle() {
        return title;
    }

    public String getHref() {
        return href;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Hyperlink hyperlink = (Hyperlink) o;
        return Objects.equals(title, hyperlink.title) &&
                Objects.equals(href, hyperlink.href) &&
                Objects.equals(type, hyperlink.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, href, type);
    }

    @Override
    public String toString() {
        return "Hyperlink{" +
                "title='" + title + '\'' +
                ", href='" + href + '\'' +
                ", type=" + type +
                '}';
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

        public HyperlinkBuilder type(final String type) {
            this.instance.type = type;
            return this;
        }

        public HyperlinkBuilder type(final HyperlinkType type) {
            this.instance.type = type.name();
            return this;
        }

        public Hyperlink build() {
            return instance;
        }
    }
}
