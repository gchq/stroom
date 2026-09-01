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

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.StringReader;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decisive de-risk for the stepping representation change: proves that persisting an element's XML output
 * as SAX events (through the binary codec) and rendering it for display later is <b>byte-identical</b> to how
 * it is serialised today.
 * <p>
 * Today: live SAX -> TinyTree -> {@code EventListUtils.getXML(NodeInfo)} (Saxon). The two getXML overloads use
 * different serialisers (Saxon vs JAXP), so display text must stay on the Saxon path. This shows the whole
 * chain - live SAX -> events -> binary -> events -> rebuilt TinyTree -> Saxon serialise - reproduces the same
 * text, so the golden {@code ~STEPPING~} corpus stays green when the store switches to events.
 */
class TestEventReplayFidelity {

    private static final String XML = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>"
            + "<records xmlns=\"records:2\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" version=\"2.0\">"
            + "<record><data name=\"Date\" value=\"01/01/2010\"/>"
            + "<data name=\"Message\" value=\"a &lt;b&gt; &amp; &quot;c&quot; å 𐍈\"/>"
            + "<nested><child/></nested></record>"
            + "</records>";

    @Test
    void displayTextIsByteIdenticalThroughEventsAndCodec() throws Exception {
        // Today's path: parse straight into a TinyTree and Saxon-serialise it.
        final String currentText = EventListUtils.getXML(parseToNodeInfo(XML));

        // The store's path: parse to events, through the binary codec, rebuild a TinyTree, Saxon-serialise.
        final EventList events = parseToEvents(XML);
        final EventList decoded = EventListSerializer.fromBytes(EventListSerializer.toBytes(events));
        final String viaEvents = EventListUtils.getXML(buildNodeInfo(decoded));

        assertThat(viaEvents)
                .as("events -> codec -> tree -> Saxon must match a direct parse -> tree -> Saxon")
                .isEqualTo(currentText);
    }

    // --- helpers --------------------------------------------------------------------------------

    private static EventList parseToEvents(final String xml) throws Exception {
        final SimpleEventListBuilder builder = new SimpleEventListBuilder();
        parse(xml, builder);
        return builder.getEventList();
    }

    private static NodeInfo parseToNodeInfo(final String xml) throws Exception {
        final Configuration configuration = Configuration.newConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final ReceivingContentHandler rch = new ReceivingContentHandler();
        final TinyBuilder tinyBuilder = new TinyBuilder(pipe);
        rch.setPipelineConfiguration(pipe);
        rch.setReceiver(tinyBuilder);
        parse(xml, rch);
        return tinyBuilder.getCurrentRoot();
    }

    private static NodeInfo buildNodeInfo(final EventList events) throws Exception {
        final Configuration configuration = Configuration.newConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final ReceivingContentHandler rch = new ReceivingContentHandler();
        final TinyBuilder tinyBuilder = new TinyBuilder(pipe);
        rch.setPipelineConfiguration(pipe);
        rch.setReceiver(tinyBuilder);
        events.fire(rch);
        return tinyBuilder.getCurrentRoot();
    }

    private static void parse(final String xml, final ContentHandler handler) throws Exception {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new StringReader(xml)));
    }
}
