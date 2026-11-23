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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.streams.RecordPicks;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.ai.impl.tools.streams.StreamReader.Fetched;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The Events of processed streams as one `<Events>` document, read by the tool itself, for `check_events` to
/// check: seen, an agent reading 50 events back 23 at a time, 47 s a read, to check them.
public class EventStreams {

    private final StreamReader streamReader;

    /// @param streamReader Reads streams as the user.
    @Inject
    public EventStreams(final StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    /// @param streamIds Events streams.
    /// @param cap       The most records read in all.
    /// @return Their events as one document, and how many records of each were read. The document is XML 1.1, with
    /// its declaration, where any record declares 1.1 (as Stroom writes Events), else XML 1.0 with no declaration.
    /// @throws ToolRefusal             if a stream can't be read or is not Events, or there are none, or a record
    ///                                 is not well-formed (saying its stream, event number and where).
    /// @throws StreamReader.StreamReadException if the user may not read a stream.
    public Read read(final List<Long> streamIds, final int cap) {
        Element merged = null;
        // Stroom writes Events as XML 1.1, where a control character such as a form feed in a source's value is
        // written as a character reference (&#xc;) XML 1.0 forbids: the merged document keeps the version, else it
        // isn't well-formed when read back (seen in chat 181).
        String xmlVersion = XmlTrees.XML_1_0;
        final Map<Long, Map<String, Object>> read = new LinkedHashMap<>();
        int checked = 0;
        for (final Long streamId : streamIds) {
            final Fetched first = streamReader.fetch(streamId, 0, null);
            if (!first.errors().isEmpty()) {
                throw new ToolRefusal("Stroom could not read stream " + streamId + ": "
                                      + String.join("; ", first.errors()));
            }
            if (first.streamTypeName() != null && !"Events".equals(first.streamTypeName())) {
                throw new ToolRefusal("Stream " + streamId + " is " + first.streamTypeName() + ", not "
                                      + "Events: give the Events streams processing wrote "
                                      + "(wait_for_processing lists them)");
            }
            final long count = first.totalItems() == null
                    ? 0
                    : first.totalItems();
            final long total = count == 0
                    ? 1
                    : count;
            final int take = (int) Math.min(total, cap - checked);
            final List<Fetched> bodies = new ArrayList<>();
            final List<Long> indexes = new ArrayList<>();
            bodies.add(first);
            indexes.add(0L);
            // A block at the head and the rest spread over the stream (RecordPicks): seen in testing, the first 500
            // of 6,000 events were almost all of one kind, and a clean check was taken to cover the rest.
            final List<Long> picks = RecordPicks.headAndSpread(total, take);
            // The first is read already.
            for (final long index : picks.subList(Math.min(1, picks.size()), picks.size())) {
                bodies.add(streamReader.fetch(streamId, index, null));
                indexes.add(index);
            }
            for (int i = 0; i < Math.max(0, Math.min(take, bodies.size())); i++) {
                final Element root = parseRecord(streamId, indexes.get(i), bodies.get(i));
                if (XmlTrees.XML_1_1.equals(root.getOwnerDocument().getXmlVersion())) {
                    xmlVersion = XmlTrees.XML_1_1;
                }
                if (merged == null) {
                    merged = root;
                } else {
                    final Document owner = merged.getOwnerDocument();
                    for (final Element child : XmlTrees.children(root)) {
                        merged.appendChild(owner.importNode(child, true));
                    }
                }
            }
            final Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("checked", Math.max(take, 0));
            counts.put("records", total);
            if (take < total) {
                counts.put("spread", "the events checked are a block at the stream's start and the rest spread "
                                     + "over it");
            }
            read.put(streamId, counts);
            checked += Math.max(take, 0);
            if (checked >= cap) {
                break;
            }
        }
        if (merged == null) {
            throw new ToolRefusal("No Events records to check in those streams");
        }
        return new Read(XmlTrees.serialise(merged, xmlVersion), read);
    }

    // One record, as a document; a record that is not well-formed is refused naming its stream and event number
    // (record index + 1, as fetch_events and the index number it).
    private static Element parseRecord(final long streamId, final long index, final Fetched body) {
        final String text = StreamReader.rootClosed(NullSafe.string(body.data()));
        try {
            return XmlTrees.parse(text);
        } catch (final XmlTrees.NotWellFormedException e) {
            throw WellFormedFaults.recordRefusal(text, "Event " + (index + 1) + " of stream " + streamId, e);
        }
    }


    // --------------------------------------------------------------------------------


    /// Events read from streams.
    ///
    /// @param eventsXml The events, as one `<Events>` document.
    /// @param read      Each stream's `checked` (records read) and `records` (records it holds).
    public record Read(String eventsXml, Map<Long, Map<String, Object>> read) {

    }
}
