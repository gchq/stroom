/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.xml.converter.json;

import stroom.pipeline.xml.converter.AbstractParser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonTokenId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JSONParser extends AbstractParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONParser.class);

    public static final String XML_ELEMENT_MAP = "map";
    public static final String XML_ELEMENT_ARRAY = "array";
    public static final String XML_ELEMENT_STRING = "string";
    public static final String XML_ELEMENT_NUMBER = "number";
    public static final String XML_ELEMENT_BOOLEAN = "boolean";
    public static final String XML_ELEMENT_NULL = "null";
    public static final String XML_ATTRIBUTE_KEY = "key";
    private static final String XML_TYPE_STRING = "string";
    private static final String EMPTY_STRING = "";
    private static final String NAMESPACE = "http://www.w3.org/2013/XSL/json";

    private static final Attributes EMPTY_ATTS = new AttributesImpl();
    private static final Map<JSONFactoryConfig, JsonFactory> JSON_FACTORY_MAP = new ConcurrentHashMap<>();
    private final JSONFactoryConfig config;
    private final boolean addRoot;
    private Attributes atts;
    private ReaderLocator reader;

    public JSONParser(final JSONFactoryConfig config, final boolean addRoot) {
        this.config = config;
        this.addRoot = addRoot;
    }

    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        // Add a location reader so that we can inform the pipeline what the
        // current read location is.
        reader = new ReaderLocator(input.getCharacterStream());

        final JsonFactory jsonFactory = JSON_FACTORY_MAP.computeIfAbsent(config, k -> {
            final JsonFactory f = new JsonFactory();

            f.configure(Feature.ALLOW_COMMENTS, config.isAllowComments());
            f.configure(Feature.ALLOW_YAML_COMMENTS, config.isAllowYamlComments());
            f.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, config.isAllowUnquotedFieldNames());
            f.configure(Feature.ALLOW_SINGLE_QUOTES, config.isAllowSingleQuotes());
            f.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, config.isAllowUnquotedControlChars());
            f.configure(Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, config.isAllowBackslashEscapingAnyCharacter());
            f.configure(Feature.ALLOW_NUMERIC_LEADING_ZEROS, config.isAllowNumericLeadingZeros());
            f.configure(Feature.ALLOW_NON_NUMERIC_NUMBERS, config.isAllowNonNumericNumbers());
            f.configure(Feature.ALLOW_MISSING_VALUES, config.isAllowMissingValues());
            f.configure(Feature.ALLOW_TRAILING_COMMA, config.isAllowTrailingComma());

            return f;
        });

        final JsonParser jp = jsonFactory.createParser(reader);

        // Set the locator so that the filters know what the current document
        // location is.
        getContentHandler().setDocumentLocator(reader);

        // Start creating the XML output.
        startDocument();

        try {
            while (jp.nextToken() != null) {
                switch (jp.getCurrentTokenId()) {
                    case JsonTokenId.ID_START_OBJECT:
                        startElement(XML_ELEMENT_MAP, jp.getCurrentName());
                        break;
                    case JsonTokenId.ID_END_OBJECT:
                        endElement(XML_ELEMENT_MAP);
                        break;
                    case JsonTokenId.ID_START_ARRAY:
                        startElement(XML_ELEMENT_ARRAY, jp.getCurrentName());
                        break;
                    case JsonTokenId.ID_END_ARRAY:
                        endElement(XML_ELEMENT_ARRAY);
                        break;
                    case JsonTokenId.ID_STRING:
                        dataElement(XML_ELEMENT_STRING, jp.getCurrentName(), jp.getValueAsString());
                        break;
                    case JsonTokenId.ID_NUMBER_INT:
                        dataElement(XML_ELEMENT_NUMBER, jp.getCurrentName(), jp.getValueAsString());
                        break;
                    case JsonTokenId.ID_NUMBER_FLOAT:
                        dataElement(XML_ELEMENT_NUMBER, jp.getCurrentName(), jp.getValueAsString());
                        break;
                    case JsonTokenId.ID_FALSE:
                        dataElement(XML_ELEMENT_BOOLEAN, jp.getCurrentName(), jp.getValueAsString());
                        break;
                    case JsonTokenId.ID_TRUE:
                        dataElement(XML_ELEMENT_BOOLEAN, jp.getCurrentName(), jp.getValueAsString());
                        break;
                    case JsonTokenId.ID_NULL:
                        dataElement(XML_ELEMENT_NULL, jp.getCurrentName(), jp.getValueAsString());
                        break;
                }
            }
        } catch (final JsonParseException e) {
            final LocatorImpl locator = new LocatorImpl();
            locator.setLineNumber(e.getLocation().getLineNr());
            locator.setColumnNumber(e.getLocation().getColumnNr());
            getErrorHandler().fatalError(new SAXParseException(e.getMessage(), locator));
//            throw e;
        } catch (final RuntimeException e) {
            getErrorHandler().fatalError(new SAXParseException(e.getMessage(), null));
//            throw e;
        } finally {
            reader.close();
            endDocument();
        }
    }

    private void startDocument() throws SAXException {
        getContentHandler().startDocument();
        getContentHandler().startPrefixMapping(EMPTY_STRING, NAMESPACE);

        if (addRoot) {
            startElement(XML_ELEMENT_MAP, null);
        }
    }

    private void endDocument() throws SAXException {
        if (addRoot) {
            endElement(XML_ELEMENT_MAP);
        }

        getContentHandler().endPrefixMapping(EMPTY_STRING);
        getContentHandler().endDocument();
    }

    private void startElement(final String elementName, final String key) throws SAXException {
        LOGGER.trace("startElm");
        atts = EMPTY_ATTS;
        if (key != null) {
            final AttributesImpl newAtts = new AttributesImpl();
            newAtts.addAttribute(EMPTY_STRING, XML_ATTRIBUTE_KEY, XML_ATTRIBUTE_KEY, XML_TYPE_STRING, key);
            atts = newAtts;
        }

        getContentHandler().startElement(NAMESPACE, elementName, elementName, atts);
    }

    private void endElement(final String elementName) throws SAXException {
        LOGGER.trace("endElm");
        getContentHandler().endElement(NAMESPACE, elementName, elementName);
    }

    private void dataElement(final String elementName, final String key, final String value) throws SAXException {
        startElement(elementName, key);
        characters(value);
        endElement(elementName);
    }

    private void characters(final String value) throws SAXException {
        if (value != null) {
            final char[] ch = value.toCharArray();
            getContentHandler().characters(ch, 0, ch.length);
        }
    }
}
