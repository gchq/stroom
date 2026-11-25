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

package stroom.ai.impl.tools.processing;

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.XmlTrees;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/// What a translation XSLT reads, as text the survey and the catalogue's check read records from (decided with the
/// user 2026-11-25: a pipeline's records are read at its translation's input, a pre-processor's output, not the
/// parser's). Stepping captures each element's input, one document a record; these are made into one text a stream:
///
/// - Stroom's XML of JSON (`http://www.w3.org/2013/XSL/json`), as the JSON parser writes it, back into JSON: a JSON
///   array of the records (the JSON the translation reads, as the parser would have read it);
/// - Stroom's records (`records:2`), as the Data Splitter writes them, into a JSON array of objects, each record's
///   `data` names and values (a nested `data` an object), so their conditions test the same names;
/// - any other XML kept as XML: the documents' records under the first document's root.
///
/// Only the formats' own structure is known here; nothing of any source.
public final class TranslationInputText {

    /// Stroom's namespace for XML of JSON.
    public static final String JSON_NAMESPACE = "http://www.w3.org/2013/XSL/json";
    /// Stroom's namespace for records.
    public static final String RECORDS_NAMESPACE = "records:2";

    private static final String DATA = "data";

    private TranslationInputText() {
        // Static only.
    }

    /// @param documents The translation's input for each record stepped, in order (blank ones are skipped).
    /// @return One text of them all, and how it is to be read; empty when none parses.
    public static Optional<Text> of(final List<String> documents) {
        final List<Element> roots = new ArrayList<>();
        // A stream stepped record by record may give the same document for each: read once.
        final Set<String> seen = new LinkedHashSet<>();
        for (final String document : documents) {
            if (document == null || document.isBlank() || !seen.add(document.strip())) {
                continue;
            }
            XmlTrees.tryParse(document).ifPresent(roots::add);
        }
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        final String namespace = roots.getFirst().getNamespaceURI();
        if (JSON_NAMESPACE.equals(namespace)) {
            final List<Object> records = new ArrayList<>();
            roots.forEach(root -> addRecords(records, json(root)));
            return Optional.of(new Text(ToolJson.dumps(records), Format.JSON, roots.size()));
        }
        if (RECORDS_NAMESPACE.equals(namespace)) {
            final List<Object> records = new ArrayList<>();
            for (final Element root : roots) {
                XmlTrees.children(root).stream()
                        .filter(child -> "record".equals(child.getLocalName()))
                        .forEach(record -> records.add(dataOf(record)));
            }
            return Optional.of(new Text(ToolJson.dumps(records), Format.JSON, roots.size()));
        }
        return Optional.of(new Text(merged(roots), Format.XML, roots.size()));
    }

    /// @param element An element of Stroom's XML of JSON: `map`, `array`, `string`, `number`, `boolean` or `null`.
    /// @return Its JSON value: a map, a list, a string, a number (as written), a boolean or null.
    static Object json(final Element element) {
        final String name = element.getLocalName();
        return switch (name) {
            case "map" -> {
                final Map<String, Object> out = new LinkedHashMap<>();
                XmlTrees.children(element).forEach(child -> out.put(child.getAttribute("key"), json(child)));
                yield out;
            }
            case "array" -> {
                final List<Object> out = new ArrayList<>();
                XmlTrees.children(element).forEach(child -> out.add(json(child)));
                yield out;
            }
            case "number" -> number(XmlTrees.allText(element).strip());
            case "boolean" -> Boolean.parseBoolean(XmlTrees.allText(element).strip());
            case "null" -> null;
            default -> XmlTrees.allText(element);
        };
    }

    // A record's data, by name: a value, or the data nested in it as an object.
    private static Map<String, Object> dataOf(final Element record) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Element data : XmlTrees.children(record)) {
            if (!DATA.equals(data.getLocalName())) {
                continue;
            }
            final List<Element> nested = XmlTrees.children(data).stream()
                    .filter(child -> DATA.equals(child.getLocalName()))
                    .toList();
            out.put(data.getAttribute("name"), nested.isEmpty()
                    ? data.getAttribute("value")
                    : dataOf(data));
        }
        return out;
    }

    // A document's records: an array's items, else the value itself.
    private static void addRecords(final List<Object> records, final Object value) {
        if (value instanceof final List<?> items) {
            records.addAll(items);
        } else if (value != null) {
            records.add(value);
        }
    }

    // A number as written: an integer, else a decimal, else the text.
    private static Object number(final String text) {
        try {
            return Long.parseLong(text);
        } catch (final NumberFormatException notLong) {
            try {
                return Double.parseDouble(text);
            } catch (final NumberFormatException notDouble) {
                return text;
            }
        }
    }

    // The documents' records under the first document's root, as XML.
    private static String merged(final List<Element> roots) {
        final Element first = roots.getFirst();
        for (final Element other : roots.subList(1, roots.size())) {
            for (final Node child : XmlTrees.children(other)) {
                first.appendChild(first.getOwnerDocument().importNode(child, true));
            }
        }
        try {
            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(first), new StreamResult(out));
            return out.toString();
        } catch (final TransformerException e) {
            throw new IllegalStateException("The translation's input couldn't be written: " + e.getMessage(), e);
        }
    }


    // --------------------------------------------------------------------------------


    /// How the text is read.
    public enum Format {
        /// A JSON array of the records.
        JSON,
        /// XML.
        XML
    }


    // --------------------------------------------------------------------------------


    /// The translation's input as one text.
    ///
    /// @param text      The text.
    /// @param format    How it is read.
    /// @param documents How many distinct documents it was made of.
    public record Text(String text, Format format, int documents) {

    }
}
