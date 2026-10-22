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

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.Fetched;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The Events of processed streams as one `<Events>` document, read by the tool itself, for `check_events` to
/// check: seen, an agent reading 50 events back 23 at a time, 47 s a read, to check them. Ported from the Stroom
/// MCP server's `_stream_events`.
public class EventStreams {

    private final StreamReader streamReader;

    /// @param streamReader Reads streams as the user.
    @Inject
    public EventStreams(final StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    /// @param streamIds Events streams.
    /// @param cap       The most records read in all (the Stroom MCP server's `max_sample_records`).
    /// @return Their events as one document, and how many records of each were read.
    /// @throws ValidationException             if a stream can't be read or is not Events, or there are none.
    /// @throws StreamReader.StreamReadException if the user may not read a stream.
    public Read read(final List<Long> streamIds, final int cap) {
        Element merged = null;
        final Map<Long, Map<String, Object>> read = new LinkedHashMap<>();
        int checked = 0;
        for (final Long streamId : streamIds) {
            final Fetched first = streamReader.fetch(streamId, 0, null);
            if (!first.errors().isEmpty()) {
                throw new ValidationException("Stroom could not read stream " + streamId + ": "
                                              + String.join("; ", first.errors()));
            }
            if (first.streamTypeName() != null && !"Events".equals(first.streamTypeName())) {
                throw new ValidationException("Stream " + streamId + " is " + first.streamTypeName() + ", not "
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
            bodies.add(first);
            for (long index = 1; index < take; index++) {
                bodies.add(streamReader.fetch(streamId, index, null));
            }
            for (final Fetched body : bodies.subList(0, Math.max(0, Math.min(take, bodies.size())))) {
                final Element root = XsltChecks.parse(StreamReader.rootClosed(NullSafe.string(body.data())),
                        "stream " + streamId);
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
            read.put(streamId, counts);
            checked += Math.max(take, 0);
            if (checked >= cap) {
                break;
            }
        }
        if (merged == null) {
            throw new ValidationException("No Events records to check in those streams");
        }
        return new Read(XmlTrees.serialise(merged), read);
    }


    // --------------------------------------------------------------------------------


    /// Events read from streams.
    ///
    /// @param eventsXml The events, as one `<Events>` document.
    /// @param read      Each stream's `checked` (records read) and `records` (records it holds).
    public record Read(String eventsXml, Map<Long, Map<String, Object>> read) {

    }
}
