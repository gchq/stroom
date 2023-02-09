package stroom.widget.util.client;


import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Objects;
import java.util.function.Consumer;

public class HtmlBuilder {

    public static final SafeHtml EN_SPACE = SafeHtmlUtils.fromSafeConstant("&ensp;");

    private final StringBuilder sb;

    public HtmlBuilder() {
        this.sb = new StringBuilder();
    }

    public static HtmlBuilder builder() {
        return new HtmlBuilder();
    }

    // -----------------------------------------------
    // START ELEMENTS
    // -----------------------------------------------

    public HtmlBuilder bold(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, "b", attributes);
    }

    public HtmlBuilder br() {
        return emptyElement("br");
    }

    public HtmlBuilder code(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, "code", attributes);
    }

    public HtmlBuilder div(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, "div", attributes);
    }

    public HtmlBuilder hr() {
        return emptyElement("hr");
    }

    public HtmlBuilder italic(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, "i", attributes);
    }

    public HtmlBuilder para(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, "p", attributes);
    }

    public HtmlBuilder span(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, "span", attributes);
    }

    // -----------------------------------------------
    // END ELEMENTS
    // -----------------------------------------------

    private HtmlBuilder elem(final Consumer<HtmlBuilder> content,
                             final String elementName,
                             final Attribute... attributes) {
        openElement(elementName, attributes);
        content.accept(this);
        closeElement(elementName);
        return this;
    }

    private HtmlBuilder openElement(final String elementName, final Attribute... attributes) {
        sb.append("<");
        sb.append(elementName);
        appendAttributes(attributes);
        sb.append(">");
        return this;
    }

    private HtmlBuilder emptyElement(final String elementName) {
        sb.append("<");
        sb.append(elementName);
        sb.append("/>");
        return this;
    }

    private HtmlBuilder closeElement(final String elementName) {
        sb.append("</");
        sb.append(elementName);
        sb.append(">");
        return this;
    }

    private HtmlBuilder appendAttributes(final Attribute... attributes) {
        if (attributes != null) {
            for (final Attribute attribute : attributes) {
                sb.append(" ");
                sb.append(attribute.name.asString());
                sb.append("=\"");
                sb.append(attribute.value.asString());
                sb.append("\"");
            }
        }
        return this;
    }

    public HtmlBuilder append(boolean b) {
        sb.append(b);
        return this;
    }

    public HtmlBuilder append(byte num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(char c) {
        sb.append(SafeHtmlUtils.htmlEscape(c));
        return this;
    }

    public HtmlBuilder append(double num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(float num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(int num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(long num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder appendLink(final String url, final String title) {
        Objects.requireNonNull(url);
        sb.append("<a href=\"");
        append(url);
        sb.append("\" target=\"_blank\">");
        if (title != null && !title.isEmpty()) {
            append(title);
        }
        sb.append("</a>");
        return this;
    }

    public HtmlBuilder appendTrustedString(final String string) {
        sb.append(string);
        return this;
    }

    public HtmlBuilder append(final String string) {
        sb.append(SafeHtmlUtils.htmlEscape(string));
        return this;
    }

    public HtmlBuilder append(final SafeHtml safeHtml) {
        sb.append(safeHtml.asString());
        return this;
    }

    public HtmlBuilder appendEscapedLines(String text) {
        sb.append(SafeHtmlUtils.htmlEscape(text).replaceAll("\n", "<br/>"));
        return this;
    }

    public SafeHtml toSafeHtml() {
        return SafeHtmlUtils.fromTrustedString(sb.toString());
    }

    public static class Attribute {

        private final SafeHtml name;
        private final SafeHtml value;

        public Attribute(final SafeHtml name, final SafeHtml value) {
            this.name = name;
            this.value = value;
        }

        public Attribute(final String name, final String value) {
            this.name = SafeHtmlUtil.from(name);
            this.value = SafeHtmlUtil.from(value);
        }

        public static Attribute className(final String name) {
            return new Attribute("class", name);
        }

        public static Attribute style(final String name) {
            return new Attribute("style", name);
        }
    }
}
