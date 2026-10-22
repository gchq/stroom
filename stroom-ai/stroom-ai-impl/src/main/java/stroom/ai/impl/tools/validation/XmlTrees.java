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

package stroom.ai.impl.tools.validation;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/// Reads XML text (XSLTs, Events, XML Schemas) as namespace-aware DOM trees for the validation checks, as the
/// Stroom MCP server read them with lxml: a DOCTYPE is refused and nothing outside the text is ever loaded, as the
/// text comes from the model or from logs.
public final class XmlTrees {

    /// The XSLT namespace.
    public static final String XSL = "http://www.w3.org/1999/XSL/Transform";
    /// The namespace `xmlns` attributes are in.
    public static final String XMLNS = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

    private XmlTrees() {
        // Static only.
    }

    /// @param text XML text.
    /// @return Its root element.
    /// @throws NotWellFormedException if it is not well-formed XML (or has a DOCTYPE), with the line and why.
    public static Element parse(final String text) {
        try {
            final DocumentBuilder builder = factory().newDocumentBuilder();
            // Errors are not printed: a document that doesn't parse is said by the caller.
            builder.setErrorHandler(null);
            final Document document = builder.parse(new InputSource(new StringReader(Objects.requireNonNullElse(
                    text, ""))));
            return document.getDocumentElement();
        } catch (final SAXParseException e) {
            throw new NotWellFormedException(e.getLineNumber(), e.getMessage());
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new NotWellFormedException(-1, e.getMessage());
        }
    }

    /// @param element An element.
    /// @return Its child elements, in order.
    public static List<Element> children(final Node element) {
        final List<Element> children = new ArrayList<>();
        final NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof final Element child) {
                children.add(child);
            }
        }
        return children;
    }

    /// @param root An element.
    /// @return It and every element below it, in document order.
    public static List<Element> descendantsAndSelf(final Element root) {
        final List<Element> all = new ArrayList<>();
        all.add(root);
        final NodeList nodes = root.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < nodes.getLength(); i++) {
            all.add((Element) nodes.item(i));
        }
        return all;
    }

    /// @param root      An element.
    /// @param namespace A namespace.
    /// @param localName A local name, or `*` for any.
    /// @return The elements below root (and root itself) of that name, in document order.
    public static List<Element> named(final Element root, final String namespace, final String localName) {
        return descendantsAndSelf(root).stream()
                .filter(element -> namespace.equals(element.getNamespaceURI()))
                .filter(element -> "*".equals(localName) || localName.equals(element.getLocalName()))
                .toList();
    }

    /// @param element An element.
    /// @return True if it is an XSLT element.
    public static boolean isXsl(final Node element) {
        return element instanceof Element && XSL.equals(element.getNamespaceURI());
    }

    /// @param element   An element.
    /// @param localName A local name.
    /// @return True if it is the XSLT element of that name, e.g. `template`.
    public static boolean isXsl(final Node element, final String localName) {
        return isXsl(element) && localName.equals(element.getLocalName());
    }

    /// @param element An element.
    /// @param name    An attribute's name (in no namespace).
    /// @return Its value, or null if it has none (an empty value is kept, as lxml's `get` gives it).
    public static String attribute(final Element element, final String name) {
        return element.hasAttribute(name)
                ? element.getAttribute(name)
                : null;
    }

    /// @param element An element.
    /// @return Its attributes other than namespace declarations, by name as written (e.g. `Name` or
    /// `xsl:use-when`), in document order.
    public static List<Attr> attributes(final Element element) {
        final List<Attr> found = new ArrayList<>();
        final NamedNodeMap map = element.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            final Attr attr = (Attr) map.item(i);
            if (!XMLNS.equals(attr.getNamespaceURI())) {
                found.add(attr);
            }
        }
        return found;
    }

    /// @param node A node, or null.
    /// @return The prefixes in scope at it (the default namespace under the key ""), as lxml's `nsmap` gives
    /// them; empty for null.
    public static Map<String, String> namespaces(final Node node) {
        final List<Element> chain = new ArrayList<>();
        Node current = node;
        while (current instanceof final Element element) {
            chain.addFirst(element);
            current = element.getParentNode();
        }
        final Map<String, String> namespaces = new LinkedHashMap<>();
        for (final Element element : chain) {
            final NamedNodeMap map = element.getAttributes();
            for (int i = 0; i < map.getLength(); i++) {
                final Attr attr = (Attr) map.item(i);
                if (XMLNS.equals(attr.getNamespaceURI())) {
                    final String prefix = "xmlns".equals(attr.getName())
                            ? ""
                            : attr.getLocalName();
                    if (attr.getValue().isEmpty()) {
                        namespaces.remove(prefix);
                    } else {
                        namespaces.put(prefix, attr.getValue());
                    }
                }
            }
        }
        return namespaces;
    }

    /// @param node An element, or null.
    /// @return Its own text before its first child element, stripped, as the Python checks read `node.text`; ""
    /// for null.
    public static String text(final Node node) {
        if (node == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        Node child = node.getFirstChild();
        while (child != null && !(child instanceof Element)) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.COMMENT_NODE
                       || child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                break;
            }
            child = child.getNextSibling();
        }
        return sb.toString().strip();
    }

    /// @param node A node.
    /// @return All the text below it, as lxml's `itertext()` joined gives it.
    public static String allText(final Node node) {
        return Objects.requireNonNullElse(node.getTextContent(), "");
    }

    /// @param element An element.
    /// @return True if it has no child element.
    public static boolean isLeaf(final Element element) {
        return children(element).isEmpty();
    }

    /// @param element An element.
    /// @return Its ancestor elements, nearest first.
    public static List<Element> ancestors(final Element element) {
        final List<Element> found = new ArrayList<>();
        Node current = element.getParentNode();
        while (current instanceof final Element parent) {
            found.add(parent);
            current = parent.getParentNode();
        }
        return found;
    }

    /// @param node A node.
    /// @return It as XML text, without an XML declaration.
    public static String serialise(final Node node) {
        try {
            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (final TransformerException e) {
            throw new IllegalStateException("Unable to write XML: " + e.getMessage(), e);
        }
    }

    private static DocumentBuilderFactory factory() throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }


    // --------------------------------------------------------------------------------


    /// XML text that is not well-formed.
    public static class NotWellFormedException extends RuntimeException {

        private final int line;

        /// @param line    The line the parser stopped at, or -1 if unknown.
        /// @param message Why, as the parser said.
        public NotWellFormedException(final int line, final String message) {
            super(message);
            this.line = line;
        }

        /// @return The line the parser stopped at, or -1 if unknown.
        public int getLine() {
            return line;
        }
    }
}
