/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.value;

import com.google.gwt.xml.client.Document;
import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Node;
import com.google.gwt.xml.client.NodeList;
import com.google.gwt.xml.client.XMLParser;

/**
 * {@link ValueAccessor} implementation for XML values.
 *
 * <p>Paths use XPath-from-root syntax (e.g. {@code "/entry/type"}).
 * Attributes are accessed via {@code @} notation
 * (e.g. {@code "/entry/@type"}).</p>
 *
 * <h3>Namespace support</h3>
 * <p>This implementation does <b>not</b> support XML namespaces.
 * Paths match elements by local name only. This is intentional:
 * floor map values are user-defined and authored by Stroom, making
 * namespace-qualified XML unlikely in practice.</p>
 *
 * <p>If namespace support is needed in future, the extension path
 * is:</p>
 * <ol>
 *   <li>Add a namespace-prefix-to-URI mapping to the floor map
 *       settings (e.g. {@code ns} → {@code http://example.com/schema})</li>
 *   <li>Support namespace-aware path syntax
 *       (e.g. {@code /ns:entry/ns:type})</li>
 *   <li>Use {@code getElementsByTagNameNS()} /
 *       {@code getAttributeNS()} instead of the non-namespace-aware
 *       equivalents used here</li>
 *   <li>Emit {@code xmlns} declarations when serialising</li>
 * </ol>
 * <p>GWT's {@code com.google.gwt.xml.client} API already provides
 * {@code Node.getNamespaceURI()}, {@code getPrefix()}, and
 * {@code getLocalName()}, so no architectural changes to the
 * {@link ValueAccessor} interface are required.</p>
 *
 * <h3>Numeric arrays</h3>
 * <p>Numeric arrays (coordinates, transformation matrices) are stored
 * as comma-separated text content
 * (e.g. {@code <coords>500.0,500.0</coords>}).</p>
 */
public final class XmlValueAccessor implements ValueAccessor {

    static final XmlValueAccessor INSTANCE = new XmlValueAccessor();

    private XmlValueAccessor() {
        // Singleton
    }

    @Override
    public ParsedValue parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            final Document doc = XMLParser.parse(raw);
            return doc != null ? new ParsedValue(doc) : null;
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public ParsedValue createEmpty(final String rootName) {
        final String name = rootName != null && !rootName.isEmpty()
                ? rootName : "entry";
        final Document doc = XMLParser.parse(
                "<" + name + "></" + name + ">");
        return new ParsedValue(doc);
    }

    /**
     * {@inheritDoc}
     *
     * <p>XML element text and attribute values carry no type, so anything present
     * is a string: a field holding {@code 5} reads back as {@code "5"}, and CDATA reads as the
     * text it wraps. Element text is trimmed, and a whitespace-only element reads as
     * {@code null} rather than as an empty string — so the round trip is not byte-exact for
     * values with leading or trailing whitespace.</p>
     *
     * <p>This is the counterpart to {@code JsonValueAccessor.getString}, which
     * returns {@code null} for a present-but-non-string value because JSON
     * <em>is</em> typed. The two therefore disagree for the same logical field.
     * That asymmetry is a property of the formats rather than a defect in either
     * implementation, and it is asserted from both sides in the accessor contract
     * tests so that neither gets "corrected" into agreement with the other.</p>
     */
    @Override
    public String getString(final ParsedValue value,
                            final String path) {
        final Document doc = asDoc(value);
        if (doc == null || path == null) {
            return null;
        }
        final PathTarget target = resolvePath(doc, path);
        if (target == null) {
            return null;
        }
        if (target.isAttribute) {
            return target.parent.getAttribute(target.localName);
        }
        final Element elem = findChildElement(
                target.parent, target.localName);
        return elem != null ? getTextContent(elem) : null;
    }

    @Override
    public boolean hasValue(final ParsedValue value, final String path) {
        final Document doc = asDoc(value);
        if (doc == null || path == null) {
            return false;
        }
        final PathTarget target = resolvePath(doc, path);
        if (target == null) {
            return false;
        }
        if (target.isAttribute) {
            // getAttribute returns "" for a missing attribute as well as for an
            // empty one, so ask the node directly.
            return target.parent.hasAttribute(target.localName);
        }
        return findChildElement(target.parent, target.localName) != null;
    }

    @Override
    public void setString(final ParsedValue value, final String path,
                          final String textValue) {
        final Document doc = asDoc(value);
        if (doc == null || path == null) {
            return;
        }
        final PathTarget target = resolveOrCreatePath(doc, path);
        if (target == null) {
            return;
        }
        if (target.isAttribute) {
            if (textValue != null) {
                target.parent.setAttribute(target.localName,
                        textValue);
            } else {
                target.parent.removeAttribute(target.localName);
            }
        } else {
            Element elem = findChildElement(
                    target.parent, target.localName);
            if (textValue != null) {
                if (elem == null) {
                    elem = doc.createElement(target.localName);
                    target.parent.appendChild(elem);
                }
                setTextContent(elem, textValue);
            } else if (elem != null) {
                target.parent.removeChild(elem);
            }
        }
    }

    @Override
    public double[] getArray(final ParsedValue value,
                             final String path) {
        final String text = getString(value, path);
        if (text == null || text.isEmpty()) {
            return null;
        }
        return XmlValueText.parseCommaSeparatedNumbers(text);
    }

    @Override
    public void setArray(final ParsedValue value, final String path,
                         final double[] numbers) {
        if (numbers == null) {
            setString(value, path, null);
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(numbers[i]);
        }
        setString(value, path, sb.toString());
    }

    @Override
    public Double getNumber(final ParsedValue value, final String path) {
        final String text = getString(value, path);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void setNumber(final ParsedValue value, final String path,
                          final Double number) {
        setString(value, path, number != null ? String.valueOf(number) : null);
    }

    @Override
    public String serialize(final ParsedValue value) {
        final Document doc = asDoc(value);
        if (doc == null) {
            return null;
        }
        final Element root = doc.getDocumentElement();
        if (root == null) {
            return null;
        }
        return serializeElement(root);
    }

    @Override
    public boolean canParse(final String raw) {
        return raw != null && raw.trim().startsWith("<");
    }

    // ---- Internal helpers ----

    /**
     * Extracts the {@link Document} from a {@link ParsedValue}.
     */
    private static Document asDoc(final ParsedValue value) {
        if (value == null) {
            return null;
        }
        final Object backing = value.getBacking();
        return backing instanceof Document ? (Document) backing : null;
    }

    /**
     * Resolves a path like {@code "/entry/type"} or
     * {@code "/entry/@type"} to a parent element and a local
     * name. Returns {@code null} if any intermediate element is
     * missing.
     */
    private static PathTarget resolvePath(final Document doc,
                                          final String path) {
        final String[] segments = splitPath(path);
        if (segments.length == 0) {
            return null;
        }

        // Navigate to the parent of the final segment.
        Element current = doc.getDocumentElement();
        if (current == null) {
            return null;
        }

        // First segment must match the root element's local name.
        if (!localName(current.getTagName()).equals(segments[0])) {
            return null;
        }

        for (int i = 1; i < segments.length - 1; i++) {
            final Element child = findChildElement(
                    current, segments[i]);
            if (child == null) {
                return null;
            }
            current = child;
        }

        final String last = segments[segments.length - 1];
        if (last.startsWith("@")) {
            return new PathTarget(
                    current, last.substring(1), true);
        }
        return new PathTarget(current, last, false);
    }

    /**
     * Like {@link #resolvePath}, but creates intermediate elements
     * if they don't exist.
     */
    private static PathTarget resolveOrCreatePath(
            final Document doc, final String path) {
        final String[] segments = splitPath(path);
        if (segments.length == 0) {
            return null;
        }

        Element current = doc.getDocumentElement();
        if (current == null) {
            return null;
        }

        // First segment must match the root element's local name.
        if (!localName(current.getTagName()).equals(segments[0])) {
            return null;
        }

        for (int i = 1; i < segments.length - 1; i++) {
            Element child = findChildElement(
                    current, segments[i]);
            if (child == null) {
                child = doc.createElement(segments[i]);
                current.appendChild(child);
            }
            current = child;
        }

        final String last = segments[segments.length - 1];
        if (last.startsWith("@")) {
            return new PathTarget(
                    current, last.substring(1), true);
        }
        return new PathTarget(current, last, false);
    }

    /**
     * Splits a path like {@code "/entry/type"} into
     * {@code ["entry", "type"]}. Leading slash is stripped.
     */
    private static String[] splitPath(final String path) {
        String p = path;
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isEmpty()) {
            return new String[0];
        }
        return p.split("/");
    }

    /**
     * Finds the first child element whose local name (i.e. tag name with any
     * namespace prefix stripped) matches the given tag name.
     */
    private static Element findChildElement(
            final Element parent, final String tagName) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                final Element elem = (Element) child;
                if (localName(elem.getTagName()).equals(tagName)) {
                    return elem;
                }
            }
        }
        return null;
    }

    /**
     * Strips any namespace prefix (e.g. {@code "ns:type"} → {@code "type"})
     * so that path matching is namespace-agnostic, per the class-level
     * "Namespace support" note above.
     */
    private static String localName(final String qualifiedName) {
        final int colon = qualifiedName.indexOf(':');
        return colon >= 0 ? qualifiedName.substring(colon + 1) : qualifiedName;
    }

    /**
     * Gets the concatenated character data of an element's direct child nodes.
     *
     * <p>CDATA sections count as character data. XML draws a distinction between
     * a text node and a CDATA section, but that distinction is purely about
     * escaping in the source document — to anything reading the value they are the
     * same string. Ignoring CDATA here made a value written as
     * {@code <![CDATA[...]]>} read as absent.</p>
     */
    private static String getTextContent(final Element elem) {
        final StringBuilder sb = new StringBuilder();
        final NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            final short type = child.getNodeType();
            if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        final String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Sets the text content of an element, replacing any existing
     * child nodes.
     */
    private static void setTextContent(final Element elem,
                                       final String text) {
        // Remove existing children.
        while (elem.hasChildNodes()) {
            elem.removeChild(elem.getFirstChild());
        }
        if (text != null) {
            elem.appendChild(
                    elem.getOwnerDocument().createTextNode(text));
        }
    }

    /**
     * Serialises an element and its children to an XML string. GWT provides no
     * built-in DOM serialiser, so this is a simple recursive implementation.
     *
     * <p>Handles four node types: elements, text, CDATA sections and comments.
     * The first two were once the only ones handled, which meant a load-edit-save
     * round trip silently deleted the <em>contents</em> of every CDATA section and
     * every comment — and since the editor re-serialises on any object drag, that
     * happened on the most ordinary edit there is.</p>
     *
     * <p>CDATA content is re-emitted as escaped text rather than as a CDATA
     * section. The two are equivalent to every XML reader, and escaping avoids
     * having to split the payload around any literal {@code ]]>} it contains, so
     * the value survives exactly while the form is normalised. Comments are
     * re-emitted as comments, since unlike CDATA there is no equivalent form to
     * fall back on.</p>
     */
    private static String serializeElement(final Element elem) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(elem.getTagName());

        // Attributes — GWT's Element doesn't expose an attribute
        // iterator directly, but getAttributes() returns a NamedNodeMap.
        final com.google.gwt.xml.client.NamedNodeMap attrs =
                elem.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                final Node attr = attrs.item(i);
                sb.append(" ").append(attr.getNodeName())
                        .append("=\"")
                        .append(XmlValueText.escapeXml(attr.getNodeValue()))
                        .append("\"");
            }
        }

        final NodeList children = elem.getChildNodes();
        if (children.getLength() == 0) {
            sb.append("/>");
        } else {
            sb.append(">");
            for (int i = 0; i < children.getLength(); i++) {
                final Node child = children.item(i);
                final short type = child.getNodeType();
                if (type == Node.ELEMENT_NODE) {
                    sb.append(serializeElement((Element) child));
                } else if (type == Node.TEXT_NODE
                        || type == Node.CDATA_SECTION_NODE) {
                    sb.append(XmlValueText.escapeXml(child.getNodeValue()));
                } else if (type == Node.COMMENT_NODE) {
                    sb.append("<!--").append(child.getNodeValue()).append("-->");
                }
            }
            sb.append("</").append(elem.getTagName()).append(">");
        }
        return sb.toString();
    }

    /**
     * Holds the result of resolving a path: the parent element,
     * the local name of the target, and whether it's an attribute.
     */
    private static final class PathTarget {
        final Element parent;
        final String localName;
        final boolean isAttribute;

        PathTarget(final Element parent, final String localName,
                   final boolean isAttribute) {
            this.parent = parent;
            this.localName = localName;
            this.isAttribute = isAttribute;
        }
    }
}
