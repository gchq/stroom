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

package stroom.pipeline.xml.event;

import stroom.pipeline.xml.event.simple.SimpleEventListBuilder;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventListSerializer {

    /**
     * A stream exercising every event type plus the encoding's sharp edges: namespaces, a supplementary
     * (astral) character, XML-significant characters inside an attribute value, empty strings, and a locator.
     */
    @Test
    void testRoundTripsEveryEventType() throws SAXException {
        final SimpleEventListBuilder b = new SimpleEventListBuilder();

        final LocatorImpl locator = new LocatorImpl();
        locator.setPublicId("pub");
        locator.setSystemId("sys");
        locator.setLineNumber(3);
        locator.setColumnNumber(7);
        b.setDocumentLocator(locator);

        b.startDocument();
        b.startPrefixMapping("ns", "urn:test");
        b.startPrefixMapping("", "urn:default");

        final AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "id", "id", "CDATA", "42");
        atts.addAttribute("urn:test", "note", "ns:note", "CDATA", "a <b> & \"c\" å");
        // Prefixed element name: localName != qName, so the codec's element-name fields are distinguishable
        // (a plain "root"/"root" would let a field-order bug pass unnoticed).
        b.startElement("urn:test", "record", "ns:record", atts);

        // Includes U+10348 (a supplementary character) to prove surrogate pairs survive UTF-8.
        final char[] text = "hello 𐍈 world".toCharArray();
        b.characters(text, 0, text.length);

        b.processingInstruction("target", "the data");
        b.ignorableWhitespace("   ".toCharArray(), 0, 3);
        b.skippedEntity("ent");
        b.startElement("", "empty", "empty", new AttributesImpl());
        b.endElement("", "empty", "empty");
        b.endElement("urn:test", "record", "ns:record");
        b.endPrefixMapping("");
        b.endPrefixMapping("ns");
        b.endDocument();

        final EventList original = b.getEventList();

        final byte[] bytes = EventListSerializer.toBytes(original);
        final EventList roundTripped = EventListSerializer.fromBytes(bytes);

        // Firing both must reproduce the same callback stream - checked exactly at the byte level...
        assertThat(EventListSerializer.toBytes(roundTripped))
                .as("re-encoding the decoded stream must be byte-identical")
                .isEqualTo(bytes);
        // ...and the serialised XML must match, as a human-meaningful equality.
        assertThat(EventListUtils.getXML(roundTripped))
                .isEqualTo(EventListUtils.getXML(original));
    }

    @Test
    void testEmptyStreamRoundTrips() {
        final EventList empty = new SimpleEventListBuilder().getEventList();
        assertThat(EventListSerializer.toBytes(empty)).isEmpty();
        assertThat(EventListSerializer.toBytes(EventListSerializer.fromBytes(new byte[0]))).isEmpty();
    }

    @Test
    void testCharactersOverTheWriteUtfCap() throws SAXException {
        // writeUTF caps at 64KB; a single characters() event easily exceeds that on a large record, so the
        // codec must not use it. 100k chars proves the length-prefixed path is taken.
        final SimpleEventListBuilder b = new SimpleEventListBuilder();
        b.startDocument();
        b.startElement("", "big", "big", new AttributesImpl());
        final char[] big = "x".repeat(100_000).toCharArray();
        b.characters(big, 0, big.length);
        b.endElement("", "big", "big");
        b.endDocument();
        final EventList original = b.getEventList();

        final EventList roundTripped = EventListSerializer.fromBytes(EventListSerializer.toBytes(original));

        assertThat(EventListUtils.getXML(roundTripped)).isEqualTo(EventListUtils.getXML(original));
    }
}
