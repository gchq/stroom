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

package stroom.pipeline.xml.bug;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;

public class JsonValidator implements ContentHandler {

    private static final String ROW = "row";

    private final JsonFactory jsonFactory;
    private int rowCount;
    private final StringBuilder sb = new StringBuilder();
    private boolean inRow;
    private int errorCount;

    public JsonValidator() {
        jsonFactory = new JsonFactory();
        jsonFactory.configure(Feature.ALLOW_COMMENTS, false);
        jsonFactory.configure(Feature.ALLOW_YAML_COMMENTS, false);
        jsonFactory.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        jsonFactory.configure(Feature.ALLOW_SINGLE_QUOTES, true);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) {
        if (localName.equalsIgnoreCase(ROW)) {
            inRow = true;
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
        if (localName.equalsIgnoreCase(ROW)) {
            try {
                final JsonParser jp = jsonFactory.createParser(sb.toString());

                while (jp.nextToken() != null) {
                }
            } catch (final IOException e) {
                errorCount++;
                System.err.println("ERROR PARSING JSON EVENT " + rowCount + " : " + e.getMessage());
            }

            sb.setLength(0);
            inRow = false;
            rowCount++;
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
        if (inRow) {
            sb.append(ch, start, length);
        }
    }

    public int getErrorCount() {
        return errorCount;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {

    }

    @Override
    public void startDocument() throws SAXException {

    }

    @Override
    public void endDocument() throws SAXException {

    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {

    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {

    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {

    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {

    }

    @Override
    public void skippedEntity(final String name) throws SAXException {

    }
}
