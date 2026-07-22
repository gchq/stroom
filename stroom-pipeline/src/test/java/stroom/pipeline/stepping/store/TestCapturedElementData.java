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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.xml.event.EventListSerializer;
import stroom.pipeline.xml.event.EventListUtils;
import stroom.pipeline.xml.event.simple.SimpleEventListBuilder;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.StringReader;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TestCapturedElementData {

    private static final String XML = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>"
            + "<records xmlns=\"records:2\"><record><data name=\"n\" value=\"a &lt;b&gt; &amp; å 𐍈\"/>"
            + "</record></records>";

    @Test
    void serializerRoundTripsBothFormatsAndNulls() throws Exception {
        final byte[] eventBytes = saxEventBytes(XML);
        final CapturedElementData original = new CapturedElementData(
                CapturedData.text("raw input text"),   // input: a reader/text side
                CapturedData.saxEvents(eventBytes),      // output: an XML side
                false,
                true,
                true,
                null);

        final CapturedElementData rt = CapturedElementDataSerializer.fromBytes(
                CapturedElementDataSerializer.toBytes(original));

        assertThat(rt.formatInput()).isFalse();
        assertThat(rt.formatOutput()).isTrue();
        assertThat(rt.hasOutput()).isTrue();
        assertThat(rt.input().format()).isEqualTo(CapturedData.Format.TEXT);
        assertThat(rt.input().asText()).isEqualTo("raw input text");
        assertThat(rt.output().format()).isEqualTo(CapturedData.Format.SAX_EVENTS);
        assertThat(rt.output().data()).isEqualTo(eventBytes);

        // A record with both sides absent round-trips to nulls.
        final CapturedElementData empty = new CapturedElementData(null, null, false, false, false, null);
        final CapturedElementData emptyRt = CapturedElementDataSerializer.fromBytes(
                CapturedElementDataSerializer.toBytes(empty));
        assertThat(emptyRt.input()).isNull();
        assertThat(emptyRt.output()).isNull();
    }

    @Test
    void mapperRendersSaxOutputToByteIdenticalDisplayText() throws Exception {
        final byte[] eventBytes = saxEventBytes(XML);

        // What the display should be: the events rendered through the Saxon tree path (proven equal to a
        // direct parse by TestEventReplayFidelity).
        final String expected = EventListUtils.getXML(
                EventListUtils.buildNodeInfo(EventListSerializer.fromBytes(eventBytes)));

        // The full store read path: through the binary store framing and the mapper.
        final CapturedElementData stored = new CapturedElementData(
                null, CapturedData.saxEvents(eventBytes), false, true, true, null);
        final CapturedElementData readBack = CapturedElementDataSerializer.fromBytes(
                CapturedElementDataSerializer.toBytes(stored));
        final SharedElementData shared = CapturedElementDataMapper.toShared(readBack);

        assertThat(shared.getOutput())
                .as("stored SAX events must render to the same display text as the tree path")
                .isEqualTo(expected);
        assertThat(shared.getInput()).isNull();
        assertThat(shared.isHasOutput()).isTrue();
    }

    @Test
    void mapperPassesTextSidesThroughUnchanged() {
        final CapturedElementData stored = new CapturedElementData(
                CapturedData.text("plain input"), CapturedData.text("plain output"),
                false, false, true, null);
        final SharedElementData shared = CapturedElementDataMapper.toShared(stored);
        assertThat(shared.getInput()).isEqualTo("plain input");
        assertThat(shared.getOutput()).isEqualTo("plain output");
    }

    private static byte[] saxEventBytes(final String xml) throws Exception {
        final SimpleEventListBuilder builder = new SimpleEventListBuilder();
        parse(xml, builder);
        return EventListSerializer.toBytes(builder.getEventList());
    }

    private static void parse(final String xml, final ContentHandler handler) throws Exception {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new StringReader(xml)));
    }
}
