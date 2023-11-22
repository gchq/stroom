package stroom.query.impl;

import java.util.Objects;
import java.util.function.Consumer;

public class HtmlBuilder {

    private final StringBuilder sb = new StringBuilder();

    public HtmlBuilder append(final String text) {
        sb.append(text);
        return this;
    }

    public HtmlBuilder startElem(final String element) {
        sb.append("<");
        sb.append(element);
        sb.append(">");
        return this;
    }

    public HtmlBuilder startElem(final String element, final String className) {
        sb.append("<");
        sb.append(element);
        sb.append(" ");
        className(className);
        sb.append(">");
        return this;
    }

    public HtmlBuilder endElem(final String element) {
        sb.append("</");
        sb.append(element);
        sb.append(">");
        return this;
    }

    public HtmlBuilder emptyElem(final String element) {
        sb.append("<");
        sb.append(element);
        sb.append(" />");
        return this;
    }

    public HtmlBuilder emptyElem(final String element, final String className) {
        sb.append("<");
        sb.append(element);
        sb.append(" ");
        className(className);
        sb.append(" />");
        return this;
    }

    public HtmlBuilder className(final String className) {
        sb.append("class=\"");
        sb.append(className);
        sb.append("\"");
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

    public void elem(final String name, final String className, final Consumer<HtmlBuilder> consumer) {
        startElem(name, className);
        consumer.accept(this);
        endElem(name);
    }

    public void elem(final String name, final Consumer<HtmlBuilder> consumer) {
        startElem(name);
        consumer.accept(this);
        endElem(name);
    }

    public String toString() {
        return sb.toString();
    }
}
