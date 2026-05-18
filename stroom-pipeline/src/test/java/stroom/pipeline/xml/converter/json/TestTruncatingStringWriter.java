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

package stroom.pipeline.xml.converter.json;

import stroom.pipeline.xml.converter.json.JSONParser.TruncatingStringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestTruncatingStringWriter {

    final String str = "This is my nice long string for testing, well it's not that long, but never mind.";

    @Test
    void parse_truncated() throws IOException {
        final String output = doTest(20);
        assertThat(output)
                .isEqualTo(str.substring(0, 20));
    }

    @Test
    void parse_truncateToOne() throws IOException {
        final String output = doTest(1);
        assertThat(output)
                .isEqualTo("T");
    }

    @Test
    void parse_notTruncated() throws IOException {
        final String output = doTest(2000);
        assertThat(output)
                .isEqualTo(str);
    }

    private String doTest(final long truncateLength) throws IOException {
        final MyContentHandler myContentHandler = new MyContentHandler();
        final TruncatingStringWriter truncatingStringWriter = new TruncatingStringWriter(
                truncateLength, myContentHandler);

        final String str = "This is my nice long string for testing, well it's not that long, but never mind.";
        final StringReader stringReader = new StringReader(str);

        final char[] chrArray = new char[str.length() + 10];


        // Don't start at the beginning to make sure it copes
        int idx = 3;
        final int readSize = 3;
        while (true) {
            final int cnt = stringReader.read(chrArray, idx, readSize);
            if (cnt == -1) {
                break;
            }
            truncatingStringWriter.write(chrArray, idx, cnt);
            idx += cnt;
        }

        return myContentHandler.toString();
    }


    // --------------------------------------------------------------------------------


    private static final class MyContentHandler implements ContentHandler {

        private final StringWriter stringWriter = new StringWriter();

        @Override
        public void setDocumentLocator(final Locator locator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startDocument() throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void endDocument() throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
            throw new UnsupportedOperationException();

        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
                throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            stringWriter.write(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skippedEntity(final String name) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return stringWriter.toString();
        }
    }
}
