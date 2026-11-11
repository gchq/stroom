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

package stroom.ai.impl.tools.sample;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// XML read into a small tree for the sample analysis: a whole document (which must be well-formed), or the start of
/// one (the complete elements before a cut-off end or an error), and an element written back as text (with the
/// namespaces it inherits).
///
/// DTDs and external entities are not read.
public final class XmlTree {

    private XmlTree() {
        // Static methods only.
    }

    /// Reads a whole document, as `etree.fromstring` does.
    ///
    /// @param text The document.
    /// @return Its root element.
    /// @throws XmlException if it isn't one well-formed document.
    public static Element parse(final String text) {
        final Pull pull = pull(text, Integer.MAX_VALUE, false);
        if (pull.failed() || pull.root() == null || !pull.closed()) {
            throw new XmlException(pull.error() == null
                    ? "not a whole document"
                    : pull.error());
        }
        return pull.root();
    }

    /// Reads as much of a text as is XML.
    ///
    /// @param text       The text.
    /// @param maxRecords The most complete children of the root to read.
    /// @return The root (if one started), its complete children before the end or an error, whether the root was
    /// closed, and any error, with whether it was only the text's end cutting the XML off.
    static Pull pull(final String text, final int maxRecords) {
        return pull(text, maxRecords, true);
    }

    private static Pull pull(final String text, final int maxRecords, final boolean stopAtRecords) {
        final EndAwareReader in = new EndAwareReader(new StringReader(text));
        final List<Element> records = new ArrayList<>();
        Element root = null;
        Element current = null;
        boolean closed = false;
        String error = null;
        boolean cut = false;
        try {
            final XMLStreamReader reader = factory().createXMLStreamReader(in);
            while (reader.hasNext()) {
                final int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    final Element element = startElement(reader, current);
                    if (current != null) {
                        current.children.add(element);
                    } else if (root == null) {
                        root = element;
                    }
                    current = element;
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (current == null) {
                        break;
                    }
                    if (current.parent == root && current != root) {
                        records.add(current);
                        if (stopAtRecords && records.size() >= maxRecords) {
                            break;
                        }
                    }
                    if (current == root) {
                        closed = true;
                    }
                    current = current.parent;
                } else if (current != null) {
                    addContent(reader, event, current);
                }
            }
        } catch (final XMLStreamException | RuntimeException e) {
            // An end that is only cut off (the reader had read to the end of the text) is no syntax error: more
            // text may follow.
            error = String.valueOf(e.getMessage());
            cut = in.ended;
        }
        return new Pull(root, records, closed, error, cut);
    }

    private static XMLInputFactory factory() {
        final XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    private static Element startElement(final XMLStreamReader reader, final Element parent) {
        final Element element = new Element(nullIfEmpty(reader.getPrefix()), reader.getLocalName(),
                nullIfEmpty(reader.getNamespaceURI()), parent);
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            element.namespaces.add(new Namespace(nullIfEmpty(reader.getNamespacePrefix(i)),
                    reader.getNamespaceURI(i) == null
                            ? ""
                            : reader.getNamespaceURI(i)));
        }
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            element.attributes.add(new Attribute(nullIfEmpty(reader.getAttributePrefix(i)),
                    reader.getAttributeLocalName(i), nullIfEmpty(reader.getAttributeNamespace(i)),
                    reader.getAttributeValue(i)));
        }
        return element;
    }

    private static void addContent(final XMLStreamReader reader, final int event, final Element current) {
        switch (event) {
            case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                final String text = reader.getText();
                if (!current.children.isEmpty() && current.children.getLast() instanceof final Text last) {
                    current.children.set(current.children.size() - 1, new Text(last.text() + text));
                } else {
                    current.children.add(new Text(text));
                }
            }
            case XMLStreamConstants.COMMENT -> current.children.add(new Comment(reader.getText()));
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> {
                current.children.add(new Instruction(reader.getPITarget(), reader.getPIData()));
            }
            default -> {
                // Nothing else is kept.
            }
        }
    }

    private static String nullIfEmpty(final String text) {
        return text == null || text.isEmpty()
                ? null
                : text;
    }

    /// Writes an element (without the text after it): with the namespace declarations of its ancestors it doesn't
    /// redeclare after its own, an element without children as `<a/>`.
    ///
    /// @param element An element.
    /// @return Its text.
    public static String toText(final Element element) {
        final StringBuilder out = new StringBuilder();
        write(out, element, true);
        return out.toString();
    }

    private static void write(final StringBuilder out, final Element element, final boolean top) {
        out.append('<').append(element.qualifiedName());
        final List<Namespace> namespaces = new ArrayList<>(element.namespaces);
        if (top) {
            final Set<String> declared = new LinkedHashSet<>();
            namespaces.forEach(ns -> declared.add(String.valueOf(ns.prefix())));
            for (Element parent = element.parent; parent != null; parent = parent.parent) {
                for (final Namespace ns : parent.namespaces) {
                    if (declared.add(String.valueOf(ns.prefix()))) {
                        namespaces.add(ns);
                    }
                }
            }
        }
        for (final Namespace ns : namespaces) {
            out.append(ns.prefix() == null
                    ? " xmlns"
                    : " xmlns:" + ns.prefix()).append("=\"").append(escapeAttribute(ns.uri())).append('"');
        }
        for (final Attribute attribute : element.attributes) {
            out.append(' ').append(attribute.prefix() == null
                            ? attribute.localName()
                            : attribute.prefix() + ":" + attribute.localName())
                    .append("=\"").append(escapeAttribute(attribute.value())).append('"');
        }
        if (element.children.isEmpty()) {
            out.append("/>");
            return;
        }
        out.append('>');
        for (final Node child : element.children) {
            switch (child) {
                case final Element inner -> write(out, inner, false);
                case final Text text -> out.append(escapeText(text.text()));
                case final Comment comment -> out.append("<!--").append(comment.text()).append("-->");
                case final Instruction instruction -> out.append("<?").append(instruction.target())
                        .append(instruction.data() == null || instruction.data().isEmpty()
                                ? ""
                                : " " + instruction.data()).append("?>");
            }
        }
        out.append("</").append(element.qualifiedName()).append('>');
    }

    private static String escapeText(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\r", "&#13;");
    }

    private static String escapeAttribute(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;");
    }


    // --------------------------------------------------------------------------------


    /// A part of an element's content.
    sealed interface Node permits Element, Text, Comment, Instruction {

    }

    /// Text.
    ///
    /// @param text The characters, entities and CDATA sections resolved.
    record Text(String text) implements Node {

    }

    /// A comment.
    ///
    /// @param text Its text.
    record Comment(String text) implements Node {

    }

    /// A processing instruction.
    ///
    /// @param target Its target.
    /// @param data   Its data.
    record Instruction(String target, String data) implements Node {

    }

    /// A namespace declaration.
    ///
    /// @param prefix The prefix, or null for the default namespace.
    /// @param uri    The namespace.
    record Namespace(String prefix, String uri) {

    }

    /// An attribute.
    ///
    /// @param prefix    Its prefix, or null.
    /// @param localName Its local name.
    /// @param namespace Its namespace, or null.
    /// @param value     Its value.
    record Attribute(String prefix, String localName, String namespace, String value) {

        /// @return Its name as an element's attributes are keyed: `{namespace}name` when it has a namespace.
        String clarkName() {
            return namespace == null
                    ? localName
                    : "{" + namespace + "}" + localName;
        }
    }

    /// What reading as much of a text as is XML found.
    ///
    /// @param root    The root element, or null if none started.
    /// @param records The root's complete children, in order.
    /// @param closed  True if the root element was closed.
    /// @param error   What stopped the reading, or null if nothing did.
    /// @param cut     True if what stopped it was the end of the text cutting the XML off.
    public record Pull(Element root, List<Element> records, boolean closed, String error, boolean cut) {

        /// @return True if something stopped the reading.
        boolean failed() {
            return error != null;
        }

        /// @return True if the text isn't well-formed before its end, as opposed to only being cut off.
        boolean syntaxError() {
            return error != null && !cut;
        }
    }

    /// An element.
    public static final class Element implements Node {

        final String prefix;
        final String localName;
        final String namespace;
        final Element parent;
        final List<Namespace> namespaces = new ArrayList<>();
        final List<Attribute> attributes = new ArrayList<>();
        final List<Node> children = new ArrayList<>();

        /// @param prefix    Its prefix, or null.
        /// @param localName Its local name.
        /// @param namespace Its namespace, or null.
        /// @param parent    The element it is in, or null for the root.
        Element(final String prefix, final String localName, final String namespace, final Element parent) {
            this.prefix = prefix;
            this.localName = localName;
            this.namespace = namespace;
            this.parent = parent;
        }

        /// @return Its local name.
        public String localName() {
            return localName;
        }

        /// @return Its namespace, or null if it is in none.
        public String namespace() {
            return namespace;
        }

        /// @return The element it is in, or null for the root.
        public Element parent() {
            return parent;
        }

        /// @return Its attributes in document order, each by its name (`{namespace}name` when it has a namespace).
        public Map<String, String> attrib() {
            final Map<String, String> out = new LinkedHashMap<>();
            attributes.forEach(attribute -> out.put(attribute.clarkName(), attribute.value()));
            return out;
        }

        /// @param name An attribute's name, as [#attrib()] gives it.
        /// @return Its value, or null if it has no such attribute.
        public String get(final String name) {
            return attrib().get(name);
        }

        /// @return The text inside it, its own and its descendants' (comments and processing instructions left out), in
        /// document order.
        public List<String> itertext() {
            final List<String> out = new ArrayList<>();
            collectText(this, out);
            return out;
        }

        private static void collectText(final Element element, final List<String> out) {
            for (final Node child : element.children) {
                if (child instanceof final Text text) {
                    out.add(text.text());
                } else if (child instanceof final Element inner) {
                    collectText(inner, out);
                }
            }
        }

        /// @return Its name as written, with its prefix.
        String qualifiedName() {
            return prefix == null
                    ? localName
                    : prefix + ":" + localName;
        }

        /// @return Its child elements, in order.
        public List<Element> elements() {
            final List<Element> elements = new ArrayList<>();
            for (final Node child : children) {
                if (child instanceof final Element element) {
                    elements.add(element);
                }
            }
            return elements;
        }

        /// @return True if it has no child nodes but text.
        public boolean isLeaf() {
            for (final Node child : children) {
                if (!(child instanceof Text)) {
                    return false;
                }
            }
            return true;
        }

        /// @return Its text before its first child node, or "" for none.
        public String text() {
            return !children.isEmpty() && children.getFirst() instanceof final Text text
                    ? text.text()
                    : "";
        }

        /// @return It and every element inside it, in document order.
        public List<Element> iter() {
            final List<Element> all = new ArrayList<>();
            collect(this, all);
            return all;
        }

        private static void collect(final Element element, final List<Element> all) {
            all.add(element);
            for (final Element child : element.elements()) {
                collect(child, all);
            }
        }

        /// @return True if any namespace is declared on it or an element above it, the xml namespace aside.
        boolean hasNamespaces() {
            for (Element element = this; element != null; element = element.parent) {
                for (final Namespace ns : element.namespaces) {
                    if (!XMLConstants.XML_NS_URI.equals(ns.uri())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /// XML that isn't well-formed: what is wrong with it.
    public static final class XmlException extends RuntimeException {

        /// @param message What is wrong with the XML.
        XmlException(final String message) {
            super(message);
        }
    }

    // A reader that knows when it has been read to the end, so an error after that is a cut-off end.
    private static final class EndAwareReader extends Reader {

        private final Reader in;
        private boolean ended;

        EndAwareReader(final Reader in) {
            this.in = in;
        }

        @Override
        public int read(final char[] buffer, final int offset, final int length) throws IOException {
            final int read = in.read(buffer, offset, length);
            if (read < 0) {
                ended = true;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
