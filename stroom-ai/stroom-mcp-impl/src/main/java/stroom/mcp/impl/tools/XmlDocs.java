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

package stroom.mcp.impl.tools;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/// Reads XSLTs (and other XML documents' text) as namespace-aware DOM trees, for the tools that read what an XSLT
/// writes or calls. A DOCTYPE is refused and nothing outside the text is ever loaded.
final class XmlDocs {

    static final String XSL = "http://www.w3.org/1999/XSL/Transform";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XmlDocs.class);

    private XmlDocs() {
    }

    /// @param text XML text, e.g. an XSLT.
    /// @return Its root element, or empty if it is empty or not well-formed XML.
    static Optional<Element> parse(final String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            final DocumentBuilder builder = factory().newDocumentBuilder();
            // Errors are not printed: a document that doesn't parse is said by the caller.
            builder.setErrorHandler(null);
            final Document document = builder.parse(new InputSource(new StringReader(text)));
            return Optional.ofNullable(document.getDocumentElement());
        } catch (final ParserConfigurationException | SAXException | IOException | RuntimeException e) {
            LOGGER.debug(() -> "Unable to parse XML: " + e.getMessage());
            return Optional.empty();
        }
    }

    /// @param element An element.
    /// @return Its child elements, in order.
    static List<Element> children(final Element element) {
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
    static List<Element> descendantsAndSelf(final Element root) {
        final List<Element> all = new ArrayList<>();
        all.add(root);
        final NodeList nodes = root.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < nodes.getLength(); i++) {
            all.add((Element) nodes.item(i));
        }
        return all;
    }

    /// @param root      An element.
    /// @param namespace The namespace of the elements wanted.
    /// @param localName The local name of the elements wanted.
    /// @return It, if it is one, and every element below it that is one, in document order.
    static List<Element> elements(final Element root, final String namespace, final String localName) {
        return descendantsAndSelf(root).stream()
                .filter(element -> is(element, namespace, localName))
                .toList();
    }

    /// @param element   An element.
    /// @param namespace The namespace of the elements wanted.
    /// @param localName The local name of the elements wanted.
    /// @return Its first child element that is one, if any.
    static Optional<Element> child(final Element element, final String namespace, final String localName) {
        return children(element).stream()
                .filter(child -> is(child, namespace, localName))
                .findFirst();
    }

    /// @param node      A node.
    /// @param namespace A namespace, or null for none.
    /// @param localName A local name.
    /// @return True if it is an element of that name.
    static boolean is(final Node node, final String namespace, final String localName) {
        return node instanceof final Element element
               && Objects.equals(element.getNamespaceURI(), namespace)
               && localName.equals(element.getLocalName());
    }

    /// @param element An element.
    /// @param name    An attribute's name (in no namespace).
    /// @return The attribute's value, or null if the element doesn't have it.
    static String attribute(final Element element, final String name) {
        return element.hasAttribute(name)
                ? element.getAttribute(name)
                : null;
    }

    /// @param element An element.
    /// @return The values of its attributes, in the order the parser gives them, leaving out namespace declarations.
    static List<String> attributeValues(final Element element) {
        final List<String> values = new ArrayList<>();
        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Attr attribute = (Attr) attributes.item(i);
            if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                values.add(attribute.getValue());
            }
        }
        return values;
    }

    /// @param element An element.
    /// @return The text directly inside it, before its first child element (as lxml's `text`), or an empty string.
    static String leadingText(final Element element) {
        final StringBuilder sb = new StringBuilder();
        Node node = element.getFirstChild();
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(node.getNodeValue());
            }
            node = node.getNextSibling();
        }
        return sb.toString();
    }

    private static DocumentBuilderFactory factory() throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }
}
