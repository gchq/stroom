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
import stroom.util.date.DateFormatterCache;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

/// Runs a generated XSLT over records with plain Saxon, and validates the events against an event-logging XSD; and
/// finds elements in the trees.
class XsltRuns extends GenerationFixtures {

    /// Plain Saxon has no Stroom functions: the log calls (unmatched and kept-Unknown records) do nothing here.
    static final String STROOM_STUBS = "<xsl:function name=\"stroom:log\"><xsl:param name=\"level\"/><xsl:param "
                                       + "name=\"message\"/><xsl:sequence select=\"()\"/></xsl:function>"
                                       + "<xsl:function name=\"stroom:record-no\"><xsl:sequence select=\"0\"/>"
                                       + "</xsl:function>";
    static final String XSL = XmlTrees.XSL;
    /// The time the stream stroom:meta speaks of was received and created.
    static final String RECEIVED = "2026-10-12T08:00:00.000Z";
    /// The address stroom:meta says the stream was sent from.
    static final String REMOTE_ADDRESS = "192.0.2.10";
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
        return transform(xslt, xml, imports, null);
    }

    /// @param xslt    An XSLT.
    /// @param xml     The records.
    /// @param imports The documents its imports name, by href, as Stroom resolves them by name.
    /// @param logged  Where what it logs (stroom:log) goes, each `LEVEL message`; null to log nothing.
    /// @return What it writes.
    static XmlElement transform(final String xslt,
                                final String xml,
                                final Map<String, String> imports,
                                final List<String> logged) {
        String code = xslt;
        if (logged != null) {
            code = code.replace("</xsl:stylesheet>", "<xsl:function name=\"stroom:record-no\"><xsl:sequence "
                                                     + "select=\"0\"/></xsl:function></xsl:stylesheet>");
        } else if (code.contains("stroom:log(") && !code.contains("name=\"stroom:log\"")) {
            code = code.replace("</xsl:stylesheet>", STROOM_STUBS + "</xsl:stylesheet>");
        }
        try {
            final Processor processor = new Processor(false);
            if (logged != null) {
                processor.registerExtensionFunction(new LogStub(logged));
            }
            processor.registerExtensionFunction(new FormatDateStub());
            // One map for the run (the stream), as Stroom's TaskScopeMap holds put values for one pipeline process.
            final Map<String, String> kept = new HashMap<>();
            processor.registerExtensionFunction(new KeptValueStub("put", kept));
            processor.registerExtensionFunction(new KeptValueStub("get", kept));
            // stroom:meta, as a stream received and created at RECEIVED gives it.
            final Map<String, String> meta = new HashMap<>(Map.of("ReceivedTime", RECEIVED, "CreatedTime", RECEIVED,
                    "RemoteAddress", REMOTE_ADDRESS));
            processor.registerExtensionFunction(new KeptValueStub("meta", meta));
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
    // The namespace of the Events an element is in or writes: its own, or event-logging's for an XSLT element.
    private static String eventsNamespace(final XmlElement element) {
        return XmlTrees.XSL.equals(element.namespace())
                ? EVT
                : element.namespace();
    }

    /// @param localName An event-logging element's name.
    /// @return Its children of that name in its own namespace (event-logging's, or a fork's).
    static List<XmlElement> children(final XmlElement root, final String localName) {
        return root.childElements(eventsNamespace(root), localName);
    }

    /// @param root A tree.
    /// @param path Event-logging child names, `/` between them.
    /// @return The first element at the path, or null.
    static XmlElement find(final XmlElement root, final String path) {
        XmlElement at = root;
        for (final String step : path.split("/")) {
            final List<XmlElement> found = at.childElements(eventsNamespace(at), step);
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
    /// @return The first element of that name anywhere below (`.//e:name`), or null.
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

    // stroom:log(level, message), each line kept as `LEVEL message`.
    private static final class LogStub extends ExtensionFunctionDefinition {

        private final List<String> logged;

        private LogStub(final List<String> logged) {
            this.logged = logged;
        }

        @Override
        public StructuredQName getFunctionQName() {
            return new StructuredQName("stroom", "stroom", "log");
        }

        @Override
        public int getMinimumNumberOfArguments() {
            return 2;
        }

        @Override
        public int getMaximumNumberOfArguments() {
            return 2;
        }

        @Override
        public SequenceType[] getArgumentTypes() {
            return new SequenceType[]{SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING};
        }

        @Override
        public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
            return SequenceType.EMPTY_SEQUENCE;
        }

        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(final XPathContext context, final Sequence[] arguments) throws XPathException {
                    logged.add(FormatDateStub.text(arguments, 0) + " " + FormatDateStub.text(arguments, 1));
                    return EmptySequence.getInstance();
                }
            };
        }
    }

    // stroom:put(key, value), giving an empty sequence, and stroom:get(key), giving the value or an empty sequence,
    // as Stroom's are; like Stroom's, they don't declare side effects, so a test sees what Saxon's evaluation order
    // does to them.
    private static final class KeptValueStub extends ExtensionFunctionDefinition {

        private final String name;
        private final Map<String, String> kept;

        private KeptValueStub(final String name, final Map<String, String> kept) {
            this.name = name;
            this.kept = kept;
        }

        @Override
        public StructuredQName getFunctionQName() {
            return new StructuredQName("stroom", "stroom", name);
        }

        @Override
        public int getMinimumNumberOfArguments() {
            return "put".equals(name)
                    ? 2
                    : 1;
        }

        @Override
        public int getMaximumNumberOfArguments() {
            return getMinimumNumberOfArguments();
        }

        @Override
        public SequenceType[] getArgumentTypes() {
            return new SequenceType[]{SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING};
        }

        @Override
        public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
            return "put".equals(name)
                    ? SequenceType.EMPTY_SEQUENCE
                    : SequenceType.OPTIONAL_STRING;
        }

        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(final XPathContext context, final Sequence[] arguments) throws XPathException {
                    final String key = FormatDateStub.text(arguments, 0);
                    if ("put".equals(name)) {
                        kept.put(key, FormatDateStub.text(arguments, 1));
                        return EmptySequence.getInstance();
                    }
                    final String value = kept.get(key);
                    return value == null
                            ? EmptySequence.getInstance()
                            : new StringValue(value);
                }
            };
        }
    }

    // stroom:format-date as the generated XSLT calls it, close enough to Stroom's for the generator's tests: one
    // argument, epoch milliseconds (or ISO text, returned as it is); two or three, the text parsed with a Java pattern
    // in the time zone given (UTC by default). Written as ISO with milliseconds in UTC; text that doesn't parse gives
    // empty, as Stroom's does (it also logs a warning).
    private static final class FormatDateStub extends ExtensionFunctionDefinition {

        private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                .withZone(ZoneOffset.UTC);

        @Override
        public StructuredQName getFunctionQName() {
            return new StructuredQName("stroom", "stroom", "format-date");
        }

        @Override
        public int getMinimumNumberOfArguments() {
            return 1;
        }

        @Override
        public int getMaximumNumberOfArguments() {
            return 3;
        }

        @Override
        public SequenceType[] getArgumentTypes() {
            return new SequenceType[]{SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_STRING};
        }

        @Override
        public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
            return SequenceType.SINGLE_STRING;
        }

        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(final XPathContext context, final Sequence[] arguments) throws XPathException {
                    final String value = text(arguments, 0);
                    if (value == null || value.isBlank()) {
                        return StringValue.EMPTY_STRING;
                    }
                    if (arguments.length == 1) {
                        return new StringValue(value.matches("-?\\d+")
                                ? ISO.format(Instant.ofEpochMilli(Long.parseLong(value)))
                                : value);
                    }
                    final String zone = arguments.length > 2 && text(arguments, 2) != null
                            ? text(arguments, 2)
                            : "UTC";
                    return new StringValue(parse(value, text(arguments, 1), zone));
                }
            };
        }

        private static String text(final Sequence[] arguments, final int n) throws XPathException {
            final Item item = arguments[n].head();
            return item == null
                    ? null
                    : item.getStringValue();
        }

        // A zone as Stroom's format-date reads it: one it doesn't read is UTC (Stroom warns).
        private static ZoneId zoneOf(final String zone) {
            try {
                return DateFormatterCache.getZoneId(zone);
            } catch (final RuntimeException e) {
                return ZoneOffset.UTC;
            }
        }

        private static String parse(final String value, final String pattern, final String zone) {
            try {
                final TemporalAccessor parsed = DateTimeFormatter.ofPattern(pattern).parse(value);
                final Instant instant;
                if (parsed.isSupported(ChronoField.OFFSET_SECONDS)) {
                    instant = Instant.from(parsed);
                } else if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
                    instant = LocalDateTime.from(parsed).atZone(zoneOf(zone)).toInstant();
                } else {
                    instant = LocalDate.from(parsed).atStartOfDay(zoneOf(zone)).toInstant();
                }
                return ISO.format(instant);
            } catch (final DateTimeException e) {
                return "";
            }
        }
    }
}
