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
import java.util.List;
import java.util.Objects;

/// [XmlNode] trees written as text, stable to the byte (the golden XSLTs pin it): attribute values escape `<`, `>`,
/// `&`, `"`, tab, new line and carriage return; text escapes `<`, `>`, `&` and carriage return; an element with nothing
/// in it is `<x/>`; and pretty-printed, each element and comment is on a line of its own, indented two spaces a level,
/// except inside an element holding text, which is written as it is.
public final class XmlSerialiser {

    /// The XML declaration written before a document.
    public static final String DECLARATION = "<?xml version='1.0' encoding='UTF-8'?>\n";

    private static final int INDENT_SIZE = 2;
    private static final int MAX_INDENT_LEVELS = 30;

    private XmlSerialiser() {
        // Static only.
    }

    /// @param node A node.
    /// @return It, and its tail, as `etree.tostring(node, encoding='unicode')` writes them.
    public static String toXml(final XmlNode node) {
        final StringBuilder out = new StringBuilder();
        write(out, node, 0, false, true);
        if (node.tail() != null) {
            out.append(escapeText(node.tail()));
        }
        return out.toString();
    }

    /// @param node A node.
    /// @return It, without its tail, as `etree.tostring(node, encoding='unicode', with_tail=False)` writes it.
    public static String toXmlWithoutTail(final XmlNode node) {
        final StringBuilder out = new StringBuilder();
        write(out, node, 0, false, true);
        return out.toString();
    }

    /// @param root An element.
    /// @return It pretty-printed, as `etree.tostring(root, pretty_print=True, encoding='unicode')` writes it, ending
    /// with a new line.
    public static String toPrettyXml(final XmlElement root) {
        final StringBuilder out = new StringBuilder();
        write(out, root, 0, true, true);
        if (root.tail() != null) {
            out.append(escapeText(root.tail()));
        }
        return out.append('\n').toString();
    }

    /// @param root An element.
    /// @return It pretty-printed after the XML declaration, as `etree.tostring(root, pretty_print=True,
    /// xml_declaration=True, encoding='UTF-8')` writes it (decoded).
    public static String toPrettyDocument(final XmlElement root) {
        return DECLARATION + toPrettyXml(root);
    }

    private static void write(final StringBuilder out,
                              final XmlNode node,
                              final int level,
                              final boolean format,
                              final boolean top) {
        switch (node) {
            case final XmlElement element -> writeElement(out, element, level, format, top);
            case final XmlComment comment -> out.append("<!--").append(comment.text()).append("-->");
            case final XmlInstruction instruction -> {
                out.append("<?").append(instruction.target());
                if (instruction.data() != null && !instruction.data().isEmpty()) {
                    out.append(' ').append(instruction.data());
                }
                out.append("?>");
            }
        }
    }

    private static void writeElement(final StringBuilder out,
                                     final XmlElement element,
                                     final int level,
                                     final boolean format,
                                     final boolean top) {
        final String name = qualified(element.binding(), element.localName());
        out.append('<').append(name);
        for (final XmlNamespace declared : declarationsWritten(element, top)) {
            out.append(' ').append(declared.prefix() == null
                    ? "xmlns"
                    : "xmlns:" + declared.prefix()).append("=\"").append(escapeAttribute(declared.uri())).append('"');
        }
        for (final XmlElement.Attribute attribute : element.attributes()) {
            out.append(' ').append(qualified(attribute.declared(), attribute.name())).append("=\"")
                    .append(escapeAttribute(attribute.value())).append('"');
        }
        if (element.children().isEmpty() && element.text() == null) {
            out.append("/>");
            return;
        }
        // An element holding text is written as it is, with no new lines or indents inside it.
        final boolean formatInside = format && !holdsText(element);
        out.append('>');
        if (formatInside) {
            out.append('\n');
        }
        if (element.text() != null) {
            out.append(escapeText(element.text()));
        }
        for (final XmlNode child : element.children()) {
            if (formatInside) {
                indent(out, level + 1);
            }
            write(out, child, level + 1, formatInside, false);
            if (child.tail() != null) {
                out.append(escapeText(child.tail()));
            }
            if (formatInside) {
                out.append('\n');
            }
        }
        if (formatInside) {
            indent(out, level);
        }
        out.append("</").append(name).append('>');
    }

    // The element's own declarations; at the top of what is written, also those of its ancestors it is in the
    // scope of (copied, so the text written stands on its own): the namespace of its name and then its attributes'
    // first, then each ancestor's declarations, nearest first.
    private static List<XmlNamespace> declarationsWritten(final XmlElement element, final boolean top) {
        final List<XmlNamespace> out = new ArrayList<>(element.declarations());
        if (!top) {
            return out;
        }
        final List<XmlNamespace> used = new ArrayList<>();
        used.add(element.binding());
        element.attributes().forEach(attribute -> used.add(attribute.declared()));
        for (final XmlNamespace declared : used) {
            if (declared != null && out.stream().noneMatch(have -> Objects.equals(have.prefix(), declared.prefix()))) {
                out.add(declared);
            }
        }
        for (final XmlElement ancestor : element.ancestors()) {
            for (final XmlNamespace declared : ancestor.declarations()) {
                if (out.stream().noneMatch(have -> Objects.equals(have.prefix(), declared.prefix()))) {
                    out.add(declared);
                }
            }
        }
        return out;
    }

    private static boolean holdsText(final XmlElement element) {
        return element.text() != null || element.children().stream().anyMatch(child -> child.tail() != null);
    }

    private static void indent(final StringBuilder out, final int level) {
        out.append(" ".repeat(INDENT_SIZE * Math.min(level, MAX_INDENT_LEVELS)));
    }

    private static String qualified(final XmlNamespace declared, final String localName) {
        return declared == null || declared.prefix() == null
                ? localName
                : declared.prefix() + ":" + localName;
    }

    /// @param text Text.
    /// @return It escaped as libxml2 escapes an attribute value.
    public static String escapeAttribute(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\n' -> out.append("&#10;");
                case '\r' -> out.append("&#13;");
                case '\t' -> out.append("&#9;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    /// @param text Text.
    /// @return It escaped as libxml2 escapes text content.
    public static String escapeText(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '\r' -> out.append("&#13;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
