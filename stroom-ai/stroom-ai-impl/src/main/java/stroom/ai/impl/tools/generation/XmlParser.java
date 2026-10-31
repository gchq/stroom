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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// XML text read into an [XmlElement] tree: namespace declarations and attributes in the order written, text and tails
/// kept (or, with `removeBlankText`, whitespace between elements left out), comments kept (or left out). DTDs and
/// external entities are not read: the text comes from the model, or from users.
public final class XmlParser {

    private XmlParser() {
        // Static only.
    }

    /// @param text XML text.
    /// @return Its root element, every node kept.
    /// @throws XmlParseException if it is not well-formed.
    public static XmlElement parse(final String text) {
        return parse(text, false, false);
    }

    /// @param text            XML text.
    /// @param removeBlankText True to leave out whitespace between elements.
    /// @param removeComments  True to leave out comments.
    /// @return Its root element.
    /// @throws XmlParseException if it is not well-formed.
    public static XmlElement parse(final String text, final boolean removeBlankText, final boolean removeComments) {
        try {
            final XMLStreamReader reader = factory().createXMLStreamReader(new StringReader(
                    Objects.requireNonNullElse(text, "")));
            return new Builder(removeBlankText, removeComments).read(reader);
        } catch (final XMLStreamException | RuntimeException e) {
            throw new XmlParseException(e.getMessage(), e);
        }
    }

    private static XMLInputFactory factory() {
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }


    // --------------------------------------------------------------------------------


    /// XML text that does not parse.
    public static final class XmlParseException extends RuntimeException {

        /// @param message Why.
        /// @param cause   What was thrown.
        public XmlParseException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Builder {

        private final boolean removeBlankText;
        private final boolean removeComments;
        private final XmlElement.Owner owner = new XmlElement.Owner();
        private final Deque<XmlElement> open = new ArrayDeque<>();
        private final Deque<Boolean> preserving = new ArrayDeque<>();
        private XmlElement root;
        private String pending;

        private Builder(final boolean removeBlankText, final boolean removeComments) {
            this.removeBlankText = removeBlankText;
            this.removeComments = removeComments;
        }

        private XmlElement read(final XMLStreamReader reader) throws XMLStreamException {
            while (reader.hasNext()) {
                final int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        flushText(false);
                        start(reader);
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        flushText(true);
                        open.pop();
                        preserving.pop();
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                        if (!open.isEmpty()) {
                            pending = (pending == null
                                    ? ""
                                    : pending) + reader.getText();
                        }
                    }
                    case XMLStreamConstants.COMMENT -> {
                        if (!open.isEmpty()) {
                            if (removeComments) {
                                // Left out: the texts either side of it are read as one, as libxml2 does.
                                flushBlank();
                            } else {
                                flushText(false);
                                open.peek().append(new XmlComment(reader.getText()));
                            }
                        }
                    }
                    case XMLStreamConstants.PROCESSING_INSTRUCTION -> {
                        if (!open.isEmpty()) {
                            flushText(false);
                            open.peek().append(new XmlInstruction(reader.getPITarget(), reader.getPIData()));
                        }
                    }
                    default -> {
                        // The declaration, DTD and document end are not part of the tree.
                    }
                }
            }
            if (root == null) {
                throw new IllegalStateException("no root element");
            }
            return root;
        }

        private void start(final XMLStreamReader reader) {
            final XmlElement parent = open.peek();
            final String namespace = reader.getNamespaceURI();
            final XmlElement element = XmlElement.parsed(owner, namespace, reader.getLocalName());
            if (parent != null) {
                parent.appendParsed(element);
            } else {
                root = element;
            }
            for (int i = 0; i < reader.getNamespaceCount(); i++) {
                final String prefix = reader.getNamespacePrefix(i);
                element.addParsedDeclaration(new XmlNamespace(prefix == null || prefix.isEmpty()
                        ? null
                        : prefix, Objects.requireNonNullElse(reader.getNamespaceURI(i), "")));
            }
            if (namespace != null && !namespace.isEmpty()) {
                final String prefix = reader.getPrefix();
                element.bind(element.searchByPrefix(prefix == null || prefix.isEmpty()
                        ? null
                        : prefix));
            }
            boolean preserve = !preserving.isEmpty() && preserving.peek();
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                final String attributeNamespace = reader.getAttributeNamespace(i);
                final String prefix = reader.getAttributePrefix(i);
                final String name = reader.getAttributeLocalName(i);
                final String value = reader.getAttributeValue(i);
                if (XMLConstants.XML_NS_URI.equals(attributeNamespace) && "space".equals(name)) {
                    preserve = "preserve".equals(value);
                }
                element.addParsedAttribute(attributeNamespace == null || attributeNamespace.isEmpty()
                        ? null
                        : declaration(element, prefix, attributeNamespace), name, value);
            }
            open.push(element);
            preserving.push(preserve);
        }

        private static XmlNamespace declaration(final XmlElement element, final String prefix, final String uri) {
            final XmlNamespace found = element.searchByPrefix(prefix);
            return found != null
                    ? found
                    : new XmlNamespace(prefix, uri);
        }

        // Text read so far, ended by markup: kept as the element's text or the last child's tail, unless it is blank
        // whitespace libxml2's remove_blank_text drops (between elements, not the only content of an element, and
        // not in an element holding text).
        private void flushText(final boolean endTag) {
            if (pending == null) {
                return;
            }
            final String text = pending;
            pending = null;
            final XmlElement element = open.peek();
            if (removeBlankText && isBlank(text) && !keepsBlank(element, endTag)) {
                return;
            }
            addText(element, text);
        }

        // A removed comment ends no text: what follows it is added to what came before.
        private void flushBlank() {
            if (pending != null && removeBlankText && isBlank(pending) && !keepsBlank(open.peek(), false)) {
                pending = null;
            }
        }

        private boolean keepsBlank(final XmlElement element, final boolean endTag) {
            if (preserving.peek()) {
                return true;
            }
            if (element.children().isEmpty()) {
                return element.text() != null || endTag;
            }
            return element.text() != null || element.children().getLast().tail() != null;
        }

        private static boolean isBlank(final String text) {
            for (int i = 0; i < text.length(); i++) {
                final char ch = text.charAt(i);
                if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') {
                    return false;
                }
            }
            return true;
        }

        private static void addText(final XmlElement element, final String text) {
            if (element.children().isEmpty()) {
                element.setText(element.text() == null
                        ? text
                        : element.text() + text);
            } else {
                final XmlNode last = element.children().getLast();
                last.setTail(last.tail() == null
                        ? text
                        : last.tail() + text);
            }
        }
    }
}
