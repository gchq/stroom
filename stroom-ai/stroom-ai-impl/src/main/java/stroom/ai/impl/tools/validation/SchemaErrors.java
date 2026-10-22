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

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

/// Validates XML text against a compiled XML Schema, as the Stroom MCP server's `errors_for` did with lxml: each
/// error with its line, the path of the element it is in and the message. The schema's messages for one fault (the
/// value, then the element holding it) are given as one error.
public final class SchemaErrors {

    private SchemaErrors() {
        // Static only.
    }

    /// @param schema A compiled schema.
    /// @param xml    An XML document's text.
    /// @return Its errors, in document order; empty if it is valid.
    /// @throws XmlTrees.NotWellFormedException if it is not well-formed.
    public static List<SchemaError> errorsFor(final Schema schema, final String xml) {
        final Collector collector = new Collector(schema.newValidatorHandler());
        try {
            final XMLReader reader = reader();
            reader.setContentHandler(collector);
            reader.setErrorHandler(collector.parseErrors());
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (final SAXParseException e) {
            throw new XmlTrees.NotWellFormedException(e.getLineNumber(), e.getMessage());
        } catch (final SAXException | IOException e) {
            throw new XmlTrees.NotWellFormedException(-1, e.getMessage());
        }
        return collector.errors();
    }

    private static XMLReader reader() throws SAXException {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            return factory.newSAXParser().getXMLReader();
        } catch (final ParserConfigurationException e) {
            throw new SAXException(e);
        }
    }


    // --------------------------------------------------------------------------------


    /// A schema error.
    ///
    /// @param line    The line it is on.
    /// @param path    The element it is in, e.g. `/Events/Event[2]/EventTime/TimeCreated` (a position is given
    ///                where the parent holds more than one element of that name).
    /// @param message What is wrong, as the validator says it.
    public record SchemaError(int line, String path, String message) {

        /// @return It as the Stroom MCP server gave it: `line`, `path` and `message`.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("line", line);
            map.put("path", path);
            map.put("message", message);
            return map;
        }
    }

    // An element on the path, and how many of each name its parent holds (filled as the parse goes on).
    private record Step(String name, int position, Map<String, Integer> siblings) {

        private String text() {
            return siblings.getOrDefault(name, 1) > 1
                    ? name + "[" + position + "]"
                    : name;
        }
    }

    // A raw error: where it was, by the steps open at the time.
    private record Raw(int line, List<Step> steps, String message) {

        private String path() {
            final StringBuilder sb = new StringBuilder();
            steps.forEach(step -> sb.append('/').append(step.text()));
            return sb.toString();
        }
    }

    // Passes the document to the validator, keeping the element path to say where each error is.
    private static final class Collector extends DefaultHandler implements ErrorHandler {

        private final ValidatorHandler validator;
        private final Deque<Step> open = new ArrayDeque<>();
        private final Deque<Map<String, Integer>> counts = new ArrayDeque<>();
        private final List<Raw> raw = new ArrayList<>();
        private Locator locator;

        private Collector(final ValidatorHandler validator) {
            this.validator = validator;
            validator.setErrorHandler(this);
            counts.push(new HashMap<>());
        }

        private ErrorHandler parseErrors() {
            return new DefaultHandler() {
                @Override
                public void fatalError(final SAXParseException e) throws SAXException {
                    throw e;
                }
            };
        }

        private List<SchemaError> errors() {
            final List<SchemaError> errors = new ArrayList<>();
            for (final Raw error : raw) {
                final String path = error.path();
                final SchemaError last = errors.isEmpty()
                        ? null
                        : errors.getLast();
                if (last != null && last.line() == error.line() && last.path().equals(path)) {
                    errors.set(errors.size() - 1, new SchemaError(last.line(), path,
                            last.message() + " " + error.message()));
                } else {
                    errors.add(new SchemaError(error.line(), path, error.message()));
                }
            }
            return errors;
        }

        @Override
        public void setDocumentLocator(final Locator locator) {
            this.locator = locator;
            validator.setDocumentLocator(locator);
        }

        @Override
        public void startDocument() throws SAXException {
            validator.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            validator.endDocument();
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
            validator.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
            validator.endPrefixMapping(prefix);
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                                 final Attributes attributes) throws SAXException {
            final Map<String, Integer> siblings = counts.peek();
            final int position = siblings.merge(localName, 1, Integer::sum);
            open.addLast(new Step(localName, position, siblings));
            counts.push(new HashMap<>());
            validator.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            validator.endElement(uri, localName, qName);
            counts.pop();
            open.removeLast();
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            validator.characters(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
            validator.ignorableWhitespace(ch, start, length);
        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {
            validator.processingInstruction(target, data);
        }

        @Override
        public void warning(final SAXParseException exception) {
            // Warnings are not errors.
        }

        @Override
        public void error(final SAXParseException exception) {
            add(exception);
        }

        @Override
        public void fatalError(final SAXParseException exception) {
            add(exception);
        }

        private void add(final SAXParseException exception) {
            final int line = exception.getLineNumber() > 0 || locator == null
                    ? exception.getLineNumber()
                    : locator.getLineNumber();
            raw.add(new Raw(line, List.copyOf(open), exception.getMessage()));
        }
    }
}
