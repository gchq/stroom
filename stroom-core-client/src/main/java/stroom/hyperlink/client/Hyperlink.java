package stroom.hyperlink.client;

import stroom.svg.client.SvgPreset;

import java.util.Objects;

/**
 * This class is used to detect table cell values that contain URL's to be turned into hyperlinks.
 * <p>
 * [:text](http://some-url/:id){:hyperlinkTarget}
 */
public class Hyperlink {
    private String text;
    private String href;
    private String type;
    private SvgPreset icon;

    private Hyperlink() {
    }

    public String getText() {
        return text;
    }

    public String getHref() {
        return href;
    }

    public String getType() {
        return type;
    }

    public SvgPreset getIcon() {
        return icon;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Hyperlink hyperlink = (Hyperlink) o;
        return Objects.equals(text, hyperlink.text) &&
                Objects.equals(href, hyperlink.href) &&
                Objects.equals(type, hyperlink.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, href, type);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (text != null) {
            sb.append("[");
            sb.append(text);
            sb.append("]");
        }
        if (href != null) {
            sb.append("(");
            sb.append(href);
            sb.append(")");
        }
        if (type != null) {
            sb.append("{");
            sb.append(type);
            sb.append("}");
        }
        return sb.toString();
    }

    public static class Builder {
        private final Hyperlink instance;

        public Builder() {
            this.instance = new Hyperlink();
        }

        public Builder text(final String text) {
            this.instance.text = text;
            return this;
        }

        public Builder href(final String href) {
            this.instance.href = href;
            return this;
        }

        public Builder type(final String type) {
            this.instance.type = type;
            return this;
        }

        public Builder type(final HyperlinkType type) {
            this.instance.type = type.name().toLowerCase();
            return this;
        }

        public Builder icon(final SvgPreset icon) {
            this.instance.icon = icon;
            return this;
        }

        public Hyperlink build() {
            return instance;
        }
    }
}
