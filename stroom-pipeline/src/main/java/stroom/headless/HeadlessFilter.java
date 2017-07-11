/*
 * Copyright 2016 Crown Copyright
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

package stroom.headless;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import stroom.pipeline.server.ErrorWriter;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.zip.HeaderMap;
import stroom.xml.event.simple.StartElement;
import stroom.xml.event.simple.StartPrefixMapping;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class HeadlessFilter extends AbstractXMLFilter implements ErrorWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeadlessFilter.class);

    private static final String URI = "event-logging:3";
    private static final String BLANK = "";
    private static final String STRING = "string";
    private static final String META_DATA = "MetaData";
    private static final String ENTRY = "Entry";
    private static final String KEY = "Key";
    private static final String VALUE = "Value";

    private static final String MESSAGE = "Message";
    private static final String LOCATION = "Location";
    private static final String ELEMENT_ID = "ElementId";
    private static final String TYPE = "Type";
    private static final String ERROR = "Error";
    private static final String SPACE = " ";
    private static final String COLON = ":";

    private HeaderMap metaData;
    private boolean started;
    private int depth;
    private StartElement root;
    private final Deque<StartPrefixMapping> prefixDeque = new ArrayDeque<>();
    private final List<StoredError> errors = new ArrayList<>();

    public void beginOutput() throws SAXException {
    }

    public void endOutput() throws SAXException {
        if (started) {
            if (depth == 1) {
                writeErrors();
                writeMetaData();
            }

            // End root element.
            if (root != null) {
                super.endElement(root.getUri(), root.getLocalName(), root.getQName());
            }

            // End stored prefix mappings.
            while (!prefixDeque.isEmpty()) {
                final StartPrefixMapping prefixMapping = prefixDeque.pop();
                super.endPrefixMapping(prefixMapping.getPrefix());
            }

            // End the document.
            super.endDocument();
        }
    }

    public void changeMetaData(final HeaderMap metaData) {
        this.metaData = metaData;

        if (depth == 1) {
            writeErrors();
            writeMetaData();
        }
    }

    private void writeMetaData() {
        if (metaData != null) {
            try {
                super.startElement(URI, META_DATA, META_DATA, new AttributesImpl());

                final List<String> keys = new ArrayList<>(metaData.keySet());
                // Make sure the metadata keys are in a consistent order
                Collections.sort(keys);

                for (final String key : keys) {
                    final AttributesImpl atts = new AttributesImpl();
                    atts.addAttribute(BLANK, KEY, KEY, STRING, key);
                    atts.addAttribute(BLANK, VALUE, VALUE, STRING, metaData.get(key));
                    super.startElement(URI, ENTRY, ENTRY, atts);
                    super.endElement(URI, ENTRY, ENTRY);
                }
                super.endElement(URI, META_DATA, META_DATA);
            } catch (final SAXException e) {
                throw ProcessException.wrap(e);
            }
            metaData = null;
        }
    }

    @Override
    public void startDocument() throws SAXException {
        if (!started) {
            started = true;
            super.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        // Deliberately suppressed.
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (depth > 1) {
            // If we are deeper than the root element then treat prefix mappings
            // as normal.
            super.startPrefixMapping(prefix, uri);

        } else if (root == null) {
            // Store prefix mappings and output them if we haven't seen the root
            // element yet.
            prefixDeque.push(new StartPrefixMapping(prefix, uri));
            super.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (depth > 1) {
            super.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (depth == 1) {
            writeErrors();
            writeMetaData();
        }

        depth++;
        if (depth > 1) {
            super.startElement(uri, localName, qName, atts);
        } else if (root == null) {
            root = new StartElement(uri, localName, qName, atts);
            super.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (depth > 1) {
            super.endElement(uri, localName, qName);
        }
        depth--;

        if (depth == 1) {
            writeErrors();
            writeMetaData();
        }
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 0) {
            super.characters(ch, start, length);
        }
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 0) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message) {
        final StoredError error = new StoredError(severity, location, elementId, message);
        if (errors.size() < 100) {
            errors.add(error);
        }
    }

    private void writeErrors() {
        if (errors.size() > 0) {
            for (final StoredError error : errors) {
                try {
                    final AttributesImpl atts = new AttributesImpl();
                    if (error.getSeverity() != null) {
                        atts.addAttribute(BLANK, TYPE, TYPE, STRING, error.getSeverity().getDisplayValue());
                    }
                    if (error.getLocation() != null) {
                        atts.addAttribute(BLANK, LOCATION, LOCATION, STRING, error.getLocation().toString());
                    }
                    atts.addAttribute(BLANK, ELEMENT_ID, ELEMENT_ID, STRING, error.getElementId());
                    atts.addAttribute(BLANK, MESSAGE, MESSAGE, STRING,
                            getMessage(error.getElementId() + COLON + SPACE + error.getMessage()));
                    super.startElement(URI, ERROR, ERROR, atts);
                    super.endElement(URI, ERROR, ERROR);
                } catch (final SAXException e) {
                    LOGGER.error("Unable to write errors!", e);
                }
            }
            errors.clear();
        }
    }

    private String getMessage(final String message) {
        final StringBuilder sb = new StringBuilder();
        if (message != null) {
            // We have a message so print it without new lines.
            final char[] chars = message.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                final char c = chars[i];
                switch (c) {
                case '\n':
                    sb.append(' ');
                    break;
                case '\r':
                    break;
                default:
                    sb.append(c);
                    break;
                }
            }
        }

        return sb.toString();
    }
}
