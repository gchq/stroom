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

import stroom.ai.impl.tools.validation.XmlTrees;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

/// Runs a generated XSLT over records with plain Saxon, as the Stroom MCP server's tests run it with SaxonC, and
/// validates the events against an event-logging XSD; and finds elements in the trees, as the tests' lxml paths do.
class XsltRuns extends GenerationFixtures {

    /// Plain Saxon has no Stroom functions: the log calls (unmatched and kept-Unknown records) do nothing here.
    static final String STROOM_STUBS = "<xsl:function name=\"stroom:log\"><xsl:param name=\"level\"/><xsl:param "
                                       + "name=\"message\"/><xsl:sequence select=\"()\"/></xsl:function>"
                                       + "<xsl:function name=\"stroom:record-no\"><xsl:sequence select=\"0\"/>"
                                       + "</xsl:function>";
    static final String XSL = XmlTrees.XSL;
    static final String EVT = XsltGenerator.EVT;

    XsltRuns() {
        // Static only, and the base of the generator tests, which use its helpers unqualified.
    }

    /// @param xslt An XSLT.
    /// @param xml  The records.
    /// @return What it writes.
    static XmlElement transform(final String xslt, final String xml) {
        return transform(xslt, xml, Map.of());
    }

    /// @param xslt    An XSLT.
    /// @param xml     The records.
    /// @param imports The documents its imports name, by href, as Stroom resolves them by name.
    /// @return What it writes.
    static XmlElement transform(final String xslt, final String xml, final Map<String, String> imports) {
        String code = xslt;
        if (code.contains("stroom:log(") && !code.contains("name=\"stroom:log\"")) {
            code = code.replace("</xsl:stylesheet>", STROOM_STUBS + "</xsl:stylesheet>");
        }
        try {
            final Processor processor = new Processor(false);
            final XsltCompiler compiler = processor.newXsltCompiler();
            compiler.setURIResolver((href, base) -> imports.containsKey(href)
                    ? new StreamSource(new StringReader(imports.get(href)), "urn:import:" + href.replace(" ", "%20"))
                    : null);
            final Xslt30Transformer transformer = compiler.compile(new StreamSource(new StringReader(code),
                    "urn:main")).load30();
            final XdmNode source = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
            final StringWriter out = new StringWriter();
            final Serializer serializer = processor.newSerializer(out);
            transformer.applyTemplates(source, serializer);
            return XmlParser.parse(out.toString());
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /// @param events What an XSLT wrote.
    /// @param xsd    The event-logging XSD.
    /// @return The schema's complaints, none if the events are valid.
    static List<String> invalid(final XmlElement events, final String xsd) {
        final List<String> errors = new ArrayList<>();
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final Schema schema = factory.newSchema(new StreamSource(new StringReader(xsd)));
            final Validator validator = schema.newValidator();
            validator.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void error(final SAXParseException e) {
                    errors.add(e.getMessage());
                }

                @Override
                public void fatalError(final SAXParseException e) {
                    errors.add(e.getMessage());
                }
            });
            validator.validate(new StreamSource(new StringReader(XmlSerialiser.toXml(events))));
        } catch (final SAXException | IOException e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    /// @param root      An element.
    /// @param localName An event-logging element's name.
    /// @return Its event-logging children of that name.
    static List<XmlElement> children(final XmlElement root, final String localName) {
        return root.childElements(EVT, localName);
    }

    /// @param root A tree.
    /// @param path Event-logging child names, `/` between them.
    /// @return The first element at the path, or null.
    static XmlElement find(final XmlElement root, final String path) {
        XmlElement at = root;
        for (final String step : path.split("/")) {
            final List<XmlElement> found = at.childElements(EVT, step);
            if (found.isEmpty()) {
                return null;
            }
            at = found.getFirst();
        }
        return at;
    }

    /// @param root A tree.
    /// @param path Event-logging child names, `/` between them.
    /// @return The text of the first element at the path, or null.
    static String text(final XmlElement root, final String path) {
        final XmlElement found = find(root, path);
        return found == null
                ? null
                : Objects.requireNonNullElse(found.text(), "");
    }

    /// @param root      A tree.
    /// @param localName An event-logging element's name.
    /// @return The first element of that name anywhere below (lxml's `.//e:name`), or null.
    static XmlElement descendant(final XmlElement root, final String localName) {
        return root.iterElements(EVT, localName).stream().filter(el -> el != root).findFirst().orElse(null);
    }

    /// @param root A tree.
    /// @param ns   A namespace.
    /// @param name A local name.
    /// @param attribute An attribute's name.
    /// @param value     Its value.
    /// @return The elements of that name below with the attribute at that value.
    static List<XmlElement> having(final XmlElement root,
                                   final String ns,
                                   final String name,
                                   final String attribute,
                                   final String value) {
        return root.iterElements(ns, name).stream().filter(el -> value.equals(el.get(attribute))).toList();
    }

    /// @param xslt A generated XSLT.
    /// @return Its stylesheet element.
    static XmlElement sheet(final String xslt) {
        return XmlParser.parse(xslt);
    }

    /// @param text Some text.
    /// @param part A part of it.
    /// @return How often the part occurs.
    static int count(final String text, final String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            n++;
        }
        return n;
    }
}
