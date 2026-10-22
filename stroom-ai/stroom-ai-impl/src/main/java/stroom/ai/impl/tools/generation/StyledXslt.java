/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The indexing and CEF XSLTs written in the Events translation's style (asked for by the user: "they should follow
/// the Events XSLT styles generally", for readability and maintainability): each part a template of its own (in its
/// own mode, or named, or inline, as [XsltStyle#layout()] says), named in the style's naming, with a comment saying
/// what it writes; and an input a template reads at least `variable_min_reads` times read once into a variable,
/// declared just before its first use (`variables`). The translation generator's own helpers are used
/// ([XsltNames], [XsltGenerator#justInTime(XmlElement)], [XsltComments]), so the three kinds of XSLT can't drift
/// apart. Ported from the Stroom MCP server's `utils/xsltstyle.py`.
public final class StyledXslt {

    /// The XML declaration the indexing and CEF XSLTs start with.
    public static final String DECLARATION = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n";

    private static final Pattern XML_DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final String XSL = MappingFacts.XSL;

    private StyledXslt() {
        // Static only.
    }

    /// @param text A stylesheet's text.
    /// @return The stylesheet, with the whitespace between its elements gone (so it is laid out afresh), but not an
    /// xsl:text's: `<xsl:text>&#10;</xsl:text>` is the new line a text output writes.
    /// @throws XmlParser.XmlParseException if it isn't well-formed.
    public static XmlElement parse(final String text) {
        final XmlElement root = XmlParser.parse(XML_DECLARATION.matcher(text).replaceFirst(""));
        // Comments too: whitespace left after one stops the layout around it.
        for (final XmlNode node : root.iter()) {
            if (node instanceof final XmlElement element && !element.is(XSL, "text") && element.text() != null
                && PyStr.isBlank(element.text()) && !element.children().isEmpty()) {
                element.setText(null);
            }
            if (node.tail() != null && PyStr.isBlank(node.tail())) {
                node.setTail(null);
            }
        }
        return root;
    }

    /// @param sheet A stylesheet.
    /// @return Its text: the declaration, then the stylesheet pretty-printed.
    public static String serialise(final XmlElement sheet) {
        return DECLARATION + XmlSerialiser.toPrettyXml(sheet);
    }

    /// @param text What a part writes.
    /// @return A comment saying it, wrapped as the generator wraps its comments.
    public static XmlComment comment(final String text) {
        return new XmlComment(XsltComments.comment(text));
    }

    /// @param text  What the part is named after.
    /// @param style The style.
    /// @param taken The names in use in the stylesheet; the name made is added.
    /// @return A template's mode or name in the style's naming, unique in the stylesheet.
    public static String partName(final String text, final XsltStyle style, final Set<String> taken) {
        final String name = XsltNames.uniqueName(XsltNames.styleName(text, style.naming()), taken, style.naming());
        taken.add(name);
        return name;
    }

    /// @param expression An XPath expression.
    /// @return Where an XPath reads it whole: not part of a longer path (Resource/URL in
    /// EventDetail/*/Resource/URL), nor followed by a step or predicate.
    static Pattern reading(final String expression) {
        return Pattern.compile("(?<![\\w/@$.:*'-])" + Pattern.quote(expression) + "(?![\\w/\\[(:-])",
                Pattern.UNICODE_CHARACTER_CLASS);
    }

    /// @param template A template.
    /// @param pattern  Where an expression is read.
    /// @return The attributes that read it: an XSL instruction's select and test, a result element's `{...}`.
    static List<Site> sites(final XmlElement template, final Pattern pattern) {
        final List<Site> out = new ArrayList<>();
        for (final XmlElement element : template.iterElements()) {
            final boolean inXsl = XSL.equals(element.namespace());
            for (final XmlElement.Attribute attribute : element.attributes()) {
                final String key = attribute.key();
                final String value = attribute.value();
                if (((inXsl && ("select".equals(key) || "test".equals(key))) || (!inXsl && value.contains("{")))
                    && pattern.matcher(value).find()) {
                    out.add(new Site(element, attribute));
                }
            }
        }
        return out;
    }

    /// Each expression in reads (-> the name to give it) that the template reads at least
    /// `style.variable_min_reads` times read once into a variable, as the Events translation does (an element's
    /// guard and its value are two reads; an object's guard a third); declared just before its first use, or at the
    /// template's start (`style.variables`).
    ///
    /// @param template A template.
    /// @param reads    Expressions, each with the name its variable is to have (before the naming is applied).
    /// @param style    The style.
    /// @param taken    The names in use in the stylesheet; those made are added.
    public static void variables(final XmlElement template,
                                 final Map<String, String> reads,
                                 final XsltStyle style,
                                 final Set<String> taken) {
        final List<XmlElement> declared = new ArrayList<>();
        for (final Map.Entry<String, String> read : reads.entrySet()) {
            final String expression = read.getKey();
            final Pattern pattern = reading(expression);
            final List<Site> sites = sites(template, pattern);
            int count = 0;
            for (final Site site : sites) {
                final Matcher matcher = pattern.matcher(site.attribute().value());
                while (matcher.find()) {
                    count++;
                }
            }
            if (count < style.variableMinReads()) {
                continue;
            }
            final String name = partName(read.getValue(), style, taken);
            for (final Site site : sites) {
                site.element().set(site.attribute().namespace(), site.attribute().name(),
                        pattern.matcher(site.attribute().value()).replaceAll(Matcher.quoteReplacement("$" + name)));
            }
            declared.add(XmlElement.create(XSL, "variable", "name", name, "select", expression));
        }
        final int at = (int) template.children().stream()
                .filter(child -> child instanceof final XmlElement element && element.is(XSL, "param"))
                .count();
        final boolean justInTime = "just_in_time".equals(style.variables());
        // just_in_time moves them last first, each before its first reader: given in reverse, they stay in read
        // order.
        final List<XmlElement> ordered = new ArrayList<>(declared);
        if (justInTime) {
            Collections.reverse(ordered);
        }
        for (int n = 0; n < ordered.size(); n++) {
            template.insert(at + n, ordered.get(n));
        }
        if (!declared.isEmpty() && justInTime) {
            XsltGenerator.justInTime(template);
        }
    }


    // --------------------------------------------------------------------------------


    /// An attribute that reads an expression.
    ///
    /// @param element   Its element.
    /// @param attribute The attribute.
    record Site(XmlElement element, XmlElement.Attribute attribute) {

    }
}
