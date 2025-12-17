/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.util.client;


import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Objects;
import java.util.function.Consumer;

public class HtmlBuilder {

    public static final SafeHtml NB_SPACE = SafeHtmlUtils.fromSafeConstant("&nbsp;");
    public static final SafeHtml EN_SPACE = SafeHtmlUtils.fromSafeConstant("&ensp;");

    private static final SafeHtml ARROW_LEFT = SafeHtmlUtils.fromTrustedString("<");
    private static final SafeHtml ARROW_RIGHT = SafeHtmlUtils.fromTrustedString(">");
    private static final SafeHtml ARROW_LEFT_SLASH = SafeHtmlUtils.fromTrustedString("</");
    private static final SafeHtml ARROW_RIGHT_SLASH = SafeHtmlUtils.fromTrustedString("/>");
    private static final SafeHtml SPACE = SafeHtmlUtils.fromTrustedString(" ");
    private static final SafeHtml QUOTE = SafeHtmlUtils.fromTrustedString("\"");
    private static final SafeHtml EQUALS_QUOTE = SafeHtmlUtils.fromTrustedString("=\"");

    private static final SafeHtml ELEMENT_B = SafeHtmlUtils.fromTrustedString("b");
    private static final SafeHtml ELEMENT_BR = SafeHtmlUtils.fromTrustedString("br");
    private static final SafeHtml ELEMENT_CODE = SafeHtmlUtils.fromTrustedString("code");
    private static final SafeHtml ELEMENT_DEL = SafeHtmlUtils.fromTrustedString("del");
    private static final SafeHtml ELEMENT_DIV = SafeHtmlUtils.fromTrustedString("div");
    private static final SafeHtml ELEMENT_HR = SafeHtmlUtils.fromTrustedString("hr");
    private static final SafeHtml ELEMENT_I = SafeHtmlUtils.fromTrustedString("i");
    private static final SafeHtml ELEMENT_INS = SafeHtmlUtils.fromTrustedString("ins");
    private static final SafeHtml ELEMENT_P = SafeHtmlUtils.fromTrustedString("p");
    private static final SafeHtml ELEMENT_SPAN = SafeHtmlUtils.fromTrustedString("span");
    private static final SafeHtml ELEMENT_TABLE = SafeHtmlUtils.fromTrustedString("table");
    private static final SafeHtml ELEMENT_TD = SafeHtmlUtils.fromTrustedString("td");
    private static final SafeHtml ELEMENT_TH = SafeHtmlUtils.fromTrustedString("th");
    private static final SafeHtml ELEMENT_TR = SafeHtmlUtils.fromTrustedString("tr");
    private static final SafeHtml ELEMENT_U = SafeHtmlUtils.fromTrustedString("u");


    private final SafeHtmlBuilder sb;

    public HtmlBuilder() {
        this.sb = new SafeHtmlBuilder();
    }

    public HtmlBuilder(final SafeHtmlBuilder sb) {
        this.sb = sb;
    }

    public static HtmlBuilder builder() {
        return new HtmlBuilder();
    }

    // -----------------------------------------------
    // START ELEMENTS
    // -----------------------------------------------

    public HtmlBuilder bold(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_B, attributes);
    }

    public HtmlBuilder bold(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_B, attributes);
    }

    public HtmlBuilder br() {
        return emptyElement(ELEMENT_BR);
    }

    public HtmlBuilder code(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_CODE, attributes);
    }

    public HtmlBuilder del(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_DEL, attributes);
    }

    public HtmlBuilder del(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_DEL, attributes);
    }

    public HtmlBuilder div(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_DIV, attributes);
    }

    public HtmlBuilder div(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_DIV, attributes);
    }

    public HtmlBuilder hr() {
        return emptyElement(ELEMENT_HR);
    }

    public HtmlBuilder ins(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_INS, attributes);
    }

    public HtmlBuilder ins(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_INS, attributes);
    }

    public HtmlBuilder italic(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_I, attributes);
    }

    public HtmlBuilder italic(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_I, attributes);
    }

    public HtmlBuilder nbsp() {
        return append(NB_SPACE);
    }

    public HtmlBuilder para(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_P, attributes);
    }

    public HtmlBuilder para(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_P, attributes);
    }

    public HtmlBuilder span(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_SPAN, attributes);
    }

    public HtmlBuilder span(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_SPAN, attributes);
    }

    public HtmlBuilder table(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_TABLE, attributes);
    }

    public HtmlBuilder td(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_TD, attributes);
    }

    public HtmlBuilder td(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_TD, attributes);
    }

    public HtmlBuilder th(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_TH, attributes);
    }

    public HtmlBuilder th(final String textContent, final Attribute... attributes) {
        return elem(textContent, ELEMENT_TH, attributes);
    }

    public HtmlBuilder tr(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_TR, attributes);
    }

    public HtmlBuilder underline(final Consumer<HtmlBuilder> content, final Attribute... attributes) {
        return elem(content, ELEMENT_U, attributes);
    }

    // -----------------------------------------------
    // END ELEMENTS
    // -----------------------------------------------

    public HtmlBuilder elem(final String textContent,
                            final SafeHtml elementName,
                            final Attribute... attributes) {
        return elem(htmlBuilder -> htmlBuilder.append(textContent), elementName, attributes);
    }

    public HtmlBuilder elem(final Consumer<HtmlBuilder> content,
                            final SafeHtml elementName,
                            final Attribute... attributes) {
        openElement(elementName, attributes);
        // Allow for empty elements
        if (content != null) {
            content.accept(this);
        }
        closeElement(elementName);
        return this;
    }

    public HtmlBuilder elem(final SafeHtml elementName,
                            final Attribute... attributes) {
        openElement(elementName, attributes);
        closeElement(elementName);
        return this;
    }

    private HtmlBuilder openElement(final SafeHtml elementName, final Attribute... attributes) {
        sb.append(ARROW_LEFT);
        sb.append(elementName);
        appendAttributes(attributes);
        sb.append(ARROW_RIGHT);
        return this;
    }

    private HtmlBuilder emptyElement(final SafeHtml elementName) {
        sb.append(ARROW_LEFT);
        sb.append(elementName);
        sb.append(ARROW_RIGHT_SLASH);
        return this;
    }

    private HtmlBuilder closeElement(final SafeHtml elementName) {
        sb.append(ARROW_LEFT_SLASH);
        sb.append(elementName);
        sb.append(ARROW_RIGHT);
        return this;
    }

    private HtmlBuilder appendAttributes(final Attribute... attributes) {
        if (attributes != null) {
            for (final Attribute attribute : attributes) {
                sb.append(SPACE);
                sb.append(attribute.name);
                sb.append(EQUALS_QUOTE);
                sb.append(attribute.value);
                sb.append(QUOTE);
            }
        }
        return this;
    }

    public HtmlBuilder append(final boolean b) {
        sb.append(b);
        return this;
    }

    public HtmlBuilder append(final byte num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(final char c) {
        sb.append(c);
        return this;
    }

    public HtmlBuilder append(final double num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(final float num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(final int num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder append(final long num) {
        sb.append(num);
        return this;
    }

    public HtmlBuilder appendLink(final String url, final String title) {
        Objects.requireNonNull(url);
        appendTrustedString("<a href=\"");
        append(url);
        appendTrustedString("\" target=\"_blank\">");
        if (title != null && !title.isEmpty()) {
            append(title);
        }
        appendTrustedString("</a>");
        return this;
    }

    public HtmlBuilder appendTrustedString(final String string) {
        sb.append(SafeHtmlUtils.fromTrustedString(string));
        return this;
    }

    public HtmlBuilder append(final String string) {
        sb.appendEscaped(string);
        return this;
    }

    public HtmlBuilder append(final SafeHtml safeHtml) {
        sb.append(safeHtml);
        return this;
    }

    public HtmlBuilder appendEscapedLines(final String text) {
        return appendTrustedString(SafeHtmlUtils.htmlEscape(text).replaceAll("\n", "<br/>"));
    }

    public SafeHtml toSafeHtml() {
        return sb.toSafeHtml();
    }


    // --------------------------------------------------------------------------------


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

        public static Attribute title(final String name) {
            return new Attribute("title", name);
        }

        public static Attribute id(final String name) {
            return new Attribute("id", name);
        }
    }
}
