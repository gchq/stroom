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

import stroom.ai.impl.tools.StreamSummaries;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/// Reading the events in a pipeline's output: counting them, picking one out, and listing every value by a
/// readable path, so a stored event and the same record stepped now can be compared.
final class EventXml {

    /// An Event element's start, with or without a prefix.
    static final Pattern EVENT = Pattern.compile("<(?:[\\w.-]+:)?Event[\\s>/]");
    /// The attributes that name an element in a path rather than being values of their own.
    static final List<String> NAMING = List.of("Name", "name", "key");
    /// The attributes left out of the values: names, and those that differ by run rather than by record.
    static final Set<String> NOT_VALUES = Set.of("Name", "name", "key", "schemaLocation", "version", "Version",
            "StreamId", "EventId");
    static final String UNPARSEABLE = "(unparseable output)";

    private static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");

    private EventXml() {
        // Static methods only.
    }

    /// @param output An element's output.
    /// @return How many events it holds.
    static int countEvents(final String output) {
        final Matcher matcher = EVENT.matcher(output == null
                ? ""
                : output);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /// @param xml An output document.
    /// @param n   Which event, from 1.
    /// @return The n-th Event element, in its root element (with the root's namespace declarations), as XML; or
    /// null if there is no such event or the document doesn't parse.
    static String nthEvent(final String xml, final int n) {
        final Document document = parse(XML_DECL.matcher(xml).replaceFirst(""));
        if (document == null) {
            return null;
        }
        final Element root = document.getDocumentElement();
        final List<Element> events = new ArrayList<>();
        final NodeList all = root.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            final Element element = (Element) all.item(i);
            if ("Event".equals(localName(element))) {
                events.add(element);
            }
        }
        if (n <= 0 || n > events.size()) {
            return null;
        }
        final Document out = StreamSummaries.documentBuilder().newDocument();
        final Element wrapper = out.createElementNS(root.getNamespaceURI(), root.getTagName());
        final NamedNodeMap attributes = root.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Attr attribute = (Attr) attributes.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                wrapper.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attribute.getName(),
                        attribute.getValue());
            }
        }
        out.appendChild(wrapper);
        wrapper.appendChild(out.importNode(events.get(n - 1), true));
        return serialise(out);
    }

    /// Every value in an output document keyed by a readable path, e.g. `Event/EventSource/User/Id`.
    ///
    /// Elements named by an attribute (event-logging Data/@Name, records:2 data/@name, JSON XML @key) get that name
    /// in the path, so reordering them does not show as a change.
    ///
    /// @param xml An output document.
    /// @return The values, by path; for a document that doesn't parse, its start under `(unparseable output)`.
    static Map<String, List<String>> fieldValues(final String xml) {
        final Map<String, List<String>> values = new LinkedHashMap<>();
        final Document document = parse(xml);
        if (document == null) {
            values.put(UNPARSEABLE, List.of(xml.substring(0, Math.min(200, xml.length()))));
            return values;
        }
        for (final Element child : children(document.getDocumentElement())) {
            walk(child, "", values);
        }
        final Map<String, List<String>> stripped = new LinkedHashMap<>();
        values.forEach((path, list) -> stripped.put(path.replaceFirst("^/+", ""), list));
        return stripped;
    }

    private static void walk(final Element node, final String path, final Map<String, List<String>> values) {
        final String name = localName(node);
        String label = null;
        for (final String attribute : NAMING) {
            if (node.hasAttribute(attribute) && !node.getAttribute(attribute).isEmpty()) {
                label = node.getAttribute(attribute);
                break;
            }
        }
        final String here = label != null
                ? path + "/" + name + "[" + label + "]"
                : path + "/" + name;
        final NamedNodeMap attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Attr attribute = (Attr) attributes.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                continue;
            }
            final String local = localName(attribute);
            if (!NOT_VALUES.contains(local)) {
                values.computeIfAbsent(here + "/@" + local, k -> new ArrayList<>()).add(attribute.getValue());
            }
        }
        final List<Element> children = children(node);
        final String text = leadingText(node).strip();
        if (!text.isEmpty() && children.isEmpty()) {
            values.computeIfAbsent(here, k -> new ArrayList<>()).add(text);
        }
        for (final Element child : children) {
            walk(child, here, values);
        }
    }

    private static String leadingText(final Element element) {
        final StringBuilder sb = new StringBuilder();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                break;
            }
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        return sb.toString();
    }

    private static List<Element> children(final Element parent) {
        final List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof final Element element) {
                children.add(element);
            }
        }
        return children;
    }

    private static String localName(final Node node) {
        return node.getLocalName() == null
                ? node.getNodeName()
                : node.getLocalName();
    }

    private static Document parse(final String xml) {
        try {
            return StreamSummaries.documentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (final Exception e) {
            return null;
        }
    }

    private static String serialise(final Document document) {
        try {
            final TransformerFactory factory = TransformerFactory.newInstance();
            final Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (final TransformerException e) {
            return null;
        }
    }
}
