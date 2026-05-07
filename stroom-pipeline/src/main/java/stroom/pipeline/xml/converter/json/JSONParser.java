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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonTokenId;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class JSONParser extends AbstractParser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JSONParser.class);

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
    private static final Map<JSONFactoryConfig, JsonMapper> JSON_MAPPER_MAP = new ConcurrentHashMap<>();

    private final JSONFactoryConfig config;
    private final boolean addRoot;
//    private Function<JsonParser, String> stringReaderFunc = null;

    public JSONParser(final boolean addRoot) {
        this.addRoot = addRoot;
        this.config = JSONFactoryConfig.builder().build();
    }

    public JSONParser(final JSONFactoryConfig config, final boolean addRoot) {
        this.config = Objects.requireNonNull(config);
        this.addRoot = addRoot;
    }

    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        // Add a location reader so that we can inform the pipeline what the
        // current read location is.
        final ReaderLocator reader = new ReaderLocator(input.getCharacterStream());
//        stringReaderFunc = config.stringTruncateLength() > 0
//                ? createReadStringWithTruncationFunc()
//                : this::readStringWithoutTruncation;

        try (reader) {
            final JsonParser jp = createJsonParser(reader);
            // Set the locator so that the filters know what the current document
            // location is.
            getContentHandler().setDocumentLocator(reader);
            // Start creating the XML output.
            startDocument();
            while (jp.nextToken() != null) {
                switch (jp.currentTokenId()) {
                    case JsonTokenId.ID_START_OBJECT -> startElement(XML_ELEMENT_MAP, jp.currentName());
                    case JsonTokenId.ID_END_OBJECT -> endElement(XML_ELEMENT_MAP);
                    case JsonTokenId.ID_START_ARRAY -> startElement(XML_ELEMENT_ARRAY, jp.currentName());
                    case JsonTokenId.ID_END_ARRAY -> endElement(XML_ELEMENT_ARRAY);
                    // We have had cases of VERY large strings that have caused OOM, so stream the value
                    case JsonTokenId.ID_STRING -> streamDataElement(XML_ELEMENT_STRING, jp.currentName(), jp);
                    case JsonTokenId.ID_NUMBER_INT,
                         JsonTokenId.ID_NUMBER_FLOAT ->
                            dataElement(XML_ELEMENT_NUMBER, jp.currentName(), jp.getValueAsString());
                    case JsonTokenId.ID_FALSE,
                         JsonTokenId.ID_TRUE ->
                            dataElement(XML_ELEMENT_BOOLEAN, jp.currentName(), jp.getValueAsString());
                    case JsonTokenId.ID_NULL -> dataElement(XML_ELEMENT_NULL, jp.currentName(), null);
                }
            }
        } catch (final JacksonException e) {
            final TokenStreamLocation tokenStreamLocation = e.getLocation();
            final LocatorImpl locator;
            if (tokenStreamLocation != null) {
                locator = new LocatorImpl();
                locator.setLineNumber(e.getLocation().getLineNr());
                locator.setColumnNumber(e.getLocation().getColumnNr());
            } else {
                locator = null;
            }
            getErrorHandler().fatalError(new SAXParseException(e.getMessage(), locator));
        } catch (final RuntimeException e) {
            getErrorHandler().fatalError(new SAXParseException(e.getMessage(), null));
        } finally {
            endDocument();
        }
    }

    private JsonParser createJsonParser(final Reader reader) {
        final JsonMapper jsonMapper = JSON_MAPPER_MAP.computeIfAbsent(config, this::createJsonMapper);
        return jsonMapper.createParser(reader);
    }

    private JsonMapper createJsonMapper(final JSONFactoryConfig config) {
        LOGGER.debug("createJsonMapper() - config: {}", config);
        // I'm sure there must be an easier way to set the streamReadConstraints
        final JsonMapper jsonMapper = JsonMapper.shared();

        final StreamReadConstraints streamReadConstraints = StreamReadConstraints.defaults()
                .rebuild()
                .maxStringLength(config.maxStringLength())
                .maxNestingDepth(config.maxDepth())
                .build();

        final JsonFactory jsonFactory = jsonMapper.tokenStreamFactory()
                .rebuild()
                .streamReadConstraints(streamReadConstraints)
                .build();

        return JsonMapper.builder(jsonFactory)
                .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, config.allowComments())
                .configure(JsonReadFeature.ALLOW_YAML_COMMENTS, config.allowYamlComments())
                .configure(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, config.allowUnquotedFieldNames())
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, config.allowSingleQuotes())
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, config.allowUnquotedControlChars())
                .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
                        config.allowBackslashEscapingAnyCharacter())
                .configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS, config.allowNumericLeadingZeros())
                .configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, config.allowNonNumericNumbers())
                .configure(JsonReadFeature.ALLOW_MISSING_VALUES, config.allowMissingValues())
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA, config.allowTrailingComma())
                .build();
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
        Attributes atts = EMPTY_ATTS;
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

    private void streamDataElement(final String elementName, final String key, final JsonParser jp)
            throws SAXException, IOException {

        startElement(elementName, key);

        final int stringTruncateLength = config.stringTruncateLength();
        LOGGER.debug("streamDataElement() - elementName: '{}', key: '{}', stringTruncateLength: {}",
                elementName, key, stringTruncateLength);
        try (final TruncatingStringWriter truncatingStringWriter = new TruncatingStringWriter(
                stringTruncateLength, getContentHandler())) {
            // Stream the string value to the contentHandler without reading the whole thing into mem
            jp.readString(truncatingStringWriter);
            LOGGER.trace(() -> LogUtil.message("readStringWithoutTruncation() - stringTruncateLength: {}, " +
                                               "consumedCount: {}, writtenCount: {}",
                    stringTruncateLength,
                    truncatingStringWriter.consumedCount,
                    truncatingStringWriter.writtenCount));

            if (truncatingStringWriter.reachedTruncatePoint) {
                final TokenStreamLocation tokenStreamLocation = jp.currentLocation();
                final LocatorImpl locator = NullSafe.get(tokenStreamLocation, location -> {
                    final LocatorImpl locator2 = new LocatorImpl();
                    locator2.setLineNumber(location.getLineNr());
                    locator2.setColumnNumber(location.getColumnNr());
                    return locator2;
                });

                getErrorHandler().warning(new SAXParseException(LogUtil.message(
                        "String value truncated to length {}, original length: {}",
                        truncatingStringWriter.getWrittenCount(),
                        truncatingStringWriter.getConsumedCount()),
                        locator));
            }
        }
        endElement(elementName);
    }

    private void characters(final String value) throws SAXException {
        if (value != null) {
            final char[] ch = value.toCharArray();
            getContentHandler().characters(ch, 0, ch.length);
        }
    }


    // --------------------------------------------------------------------------------


    // Pkg private for testing
    static class TruncatingStringWriter extends StringWriter {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TruncatingStringWriter.class);

        private final ContentHandler contentHandler;
        private final long maxLength;
        private long writtenCount = 0;
        private long consumedCount = 0;
        private boolean reachedTruncatePoint = false;

        // Pkg private for testing
        TruncatingStringWriter(final long maxLength, final ContentHandler contentHandler) {
            if (maxLength <= 0) {
                throw new IllegalArgumentException("maxLength must be > 0");
            }
            this.maxLength = maxLength;
            this.contentHandler = contentHandler;
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) {
            try {
                consumedCount += len;
                if (!reachedTruncatePoint) {
                    final long remaining = maxLength - writtenCount;
                    if (remaining > 0) {
                        if (len < remaining) {
                            LOGGER.trace("write() - off: {}, len: {}, consumedCount: {}, writtenCount: {}",
                                    off, len, consumedCount, writtenCount);
                            contentHandler.characters(cbuf, off, len);
                            writtenCount += len;
                        } else {
                            final int remainingInt = Math.toIntExact(remaining);
                            LOGGER.trace("write() - off: {}, remainingInt: {}, consumedCount: {}, writtenCount: {}",
                                    off, remainingInt, consumedCount, writtenCount);
                            contentHandler.characters(cbuf, off, remainingInt);
                            writtenCount += remainingInt;
                            reachedTruncatePoint = true;
                        }
                    } else {
                        // Past the truncation point
                        LOGGER.trace("write() - Reached truncation point, consumedCount: {}, writtenCount: {}",
                                consumedCount, writtenCount);
                        reachedTruncatePoint = true;
                    }
                } else {
                    LOGGER.trace("write() - Past truncation point, consumedCount: {}, writtenCount: {}",
                            consumedCount, writtenCount);
                }
            } catch (final SAXException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void flush() {
            // Nothing to flush
        }

        @Override
        public void close() throws IOException {
            // Don't want to close the contentHandler as we are not finished with it
        }

        /**
         * Once all write calls are done, this is the number of characters consumed, which may be
         * more that the amount written.
         */
        public long getConsumedCount() {
            return consumedCount;
        }

        public long getWrittenCount() {
            return writtenCount;
        }

        public boolean wasTruncated() {
            return consumedCount > writtenCount;
        }
    }
}
