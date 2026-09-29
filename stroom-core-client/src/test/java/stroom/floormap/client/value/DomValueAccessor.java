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

import stroom.floormap.client.editor.FloorMapEditorModel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/// A test-only [ValueAccessor] implementation backed by the JDK's
/// `javax.xml.parsers` / `org.w3c.dom` APIs, which are available
/// on the server/test classpath but NOT in GWT.
///
/// This mirrors the path conventions of the real GWT
/// `stroom.floormap.client.value.XmlValueAccessor` implementation exactly
/// (XPath-from-root syntax such as `"/entry/type"`, attributes via
/// `"/entry/@type"`, comma-separated numeric arrays), so that
/// [FloorMapEntryParser] and [FloorMapEditorModel] can be
/// exercised against XML-formatted values without any GWT dependency.
///
/// @see MapValueAccessor the equivalent JSON-flavoured test double
public class DomValueAccessor implements ValueAccessor {

    public static final DomValueAccessor INSTANCE = new DomValueAccessor();

    @Override
    public ParsedValue parse(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            final DocumentBuilder builder = newBuilder();
            final Document doc = builder.parse(
                    new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            return new ParsedValue(doc);
        } catch (final SAXException | IOException | ParserConfigurationException e) {
            return null;
        }
    }

    @Override
    public ParsedValue createEmpty(final String rootName) {
        final String name = rootName != null && !rootName.isEmpty() ? rootName : "entry";
        try {
            final Document doc = newBuilder().newDocument();
            doc.appendChild(doc.createElement(name));
            return new ParsedValue(doc);
        } catch (final ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getString(final ParsedValue value, final String path) {
        final Document doc = asDoc(value);
        if (doc == null || path == null) {
            return null;
        }
        final PathTarget target = resolvePath(doc, path);
        if (target == null) {
            return null;
        }
        if (target.isAttribute) {
            final String attr = target.parent.getAttribute(target.localName);
            return attr.isEmpty() && !target.parent.hasAttribute(target.localName) ? null : attr;
        }
        final Element elem = findChildElement(target.parent, target.localName);
        return elem != null ? getTextContent(elem) : null;
    }

    @Override
    public void setString(final ParsedValue value, final String path, final String textValue) {
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
                target.parent.setAttribute(target.localName, textValue);
            } else {
                target.parent.removeAttribute(target.localName);
            }
        } else {
            Element elem = findChildElement(target.parent, target.localName);
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
            return target.parent.hasAttribute(target.localName);
        }
        return findChildElement(target.parent, target.localName) != null;
    }

    @Override
    public double[] getArray(final ParsedValue value, final String path) {
        final String text = getString(value, path);
        if (text == null || text.isEmpty()) {
            return null;
        }
        return XmlValueText.parseCommaSeparatedNumbers(text);
    }

    @Override
    public void setArray(final ParsedValue value, final String path, final double[] numbers) {
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
    public void setNumber(final ParsedValue value, final String path, final Double number) {
        setString(value, path, number != null ? String.valueOf(number) : null);
    }

    @Override
    public String serialize(final ParsedValue value) {
        final Document doc = asDoc(value);
        if (doc == null || doc.getDocumentElement() == null) {
            return null;
        }
        return serializeElement(doc.getDocumentElement());
    }

    /// Mirrors production `XmlValueAccessor.serializeElement`, deliberately
    /// hand-rolled rather than delegating to a JAXP `Transformer`.
    ///
    /// This used to use a `Transformer`, which was a trap: the JDK
    /// serialiser is complete and correct, so round-trip tests written against this
    /// double passed while the production serialiser — a hand-written recursion
    /// over node types — silently dropped whole categories of node. The double has
    /// to be as limited as the thing it stands in for, or the tests attest to
    /// nothing.
    private static String serializeElement(final Element elem) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(elem.getTagName());

        final org.w3c.dom.NamedNodeMap attrs = elem.getAttributes();
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

    @Override
    public boolean canParse(final String raw) {
        return raw != null && raw.trim().startsWith("<");
    }

    // ---- Internal helpers (ported from the real XmlValueAccessor) ----

    private static DocumentBuilder newBuilder() throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder();
    }

    private static Document asDoc(final ParsedValue value) {
        if (value == null) {
            return null;
        }
        final Object backing = value.getBacking();
        return backing instanceof Document ? (Document) backing : null;
    }

    private static PathTarget resolvePath(final Document doc, final String path) {
        final String[] segments = splitPath(path);
        if (segments.length == 0) {
            return null;
        }

        Element current = doc.getDocumentElement();
        if (current == null || !localName(current.getTagName()).equals(segments[0])) {
            return null;
        }

        for (int i = 1; i < segments.length - 1; i++) {
            final Element child = findChildElement(current, segments[i]);
            if (child == null) {
                return null;
            }
            current = child;
        }

        final String last = segments[segments.length - 1];
        if (last.startsWith("@")) {
            return new PathTarget(current, last.substring(1), true);
        }
        return new PathTarget(current, last, false);
    }

    private static PathTarget resolveOrCreatePath(final Document doc, final String path) {
        final String[] segments = splitPath(path);
        if (segments.length == 0) {
            return null;
        }

        Element current = doc.getDocumentElement();
        if (current == null || !localName(current.getTagName()).equals(segments[0])) {
            return null;
        }

        for (int i = 1; i < segments.length - 1; i++) {
            Element child = findChildElement(current, segments[i]);
            if (child == null) {
                child = doc.createElement(segments[i]);
                current.appendChild(child);
            }
            current = child;
        }

        final String last = segments[segments.length - 1];
        if (last.startsWith("@")) {
            return new PathTarget(current, last.substring(1), true);
        }
        return new PathTarget(current, last, false);
    }

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

    private static Element findChildElement(final Element parent, final String tagName) {
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

    /// Strips any namespace prefix (e.g. `"ns:type"` → `"type"`),
    /// matching the namespace-agnostic behaviour of the real
    /// `stroom.floormap.client.value.XmlValueAccessor`.
    private static String localName(final String qualifiedName) {
        final int colon = qualifiedName.indexOf(':');
        return colon >= 0 ? qualifiedName.substring(colon + 1) : qualifiedName;
    }

    /// Mirrors production `XmlValueAccessor.getTextContent`, including its
    /// treatment of CDATA sections as character data.
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

    private static void setTextContent(final Element elem, final String text) {
        while (elem.hasChildNodes()) {
            elem.removeChild(elem.getFirstChild());
        }
        if (text != null) {
            elem.appendChild(elem.getOwnerDocument().createTextNode(text));
        }
    }

    private static final class PathTarget {
        final Element parent;
        final String localName;
        final boolean isAttribute;

        PathTarget(final Element parent, final String localName, final boolean isAttribute) {
            this.parent = parent;
            this.localName = localName;
            this.isAttribute = isAttribute;
        }
    }
}
