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

package stroom.ai.impl.tools.timeline;

import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.sequence.JoinKeys;
import stroom.ai.impl.tools.timeline.TimelineReader.RoleRead;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Each event counted once, by its raw parent stream and record.
class TestEventDedupe {

    private static final List<Source> SOURCES = List.of(source("Endpoint", true), source("Endpoint archive", true),
            source("Codes", false));

    @Test
    void theSameEventInTwoSourcesIsKeptOnceTheFirstSourcesForOneStream() {
        final List<Event> events = List.of(event("a", 0, "21", "5"), event("b", 1, "21", "5"),
                event("c", 1, "21", "6"));

        final EventDedupe.Deduped deduped = EventDedupe.dedupe(events, SOURCES, Map.of(21L, 8L)::get);

        assertThat(deduped.events()).extracting(Event::id).containsExactly("a", "c");
        assertThat(deduped.duplicates()).containsExactlyEntriesOf(Map.of(1, 1));
    }

    @Test
    void aReprocessedEventsNewestDocumentIsKeptWhicheverIsReadFirst() {
        // Streams 21 and 34 both translated from raw stream 8.
        final List<Event> events = List.of(event("new", 0, "34", "5"), event("old", 1, "21", "5"));

        final EventDedupe.Deduped deduped = EventDedupe.dedupe(events, SOURCES, Map.of(21L, 8L, 34L, 8L)::get);

        assertThat(deduped.events()).extracting(Event::id).containsExactly("new");
        assertThat(deduped.duplicates()).containsExactlyEntriesOf(Map.of(1, 1));
        // Read the other way round, still the newest.
        assertThat(EventDedupe.dedupe(events.reversed(), SOURCES, Map.of(21L, 8L, 34L, 8L)::get).events())
                .extracting(Event::id).containsExactly("new");
    }

    @Test
    void withNoParentKnownAnEventIsKnownByItsOwnStreamAndTheSameRecordOfAnotherStreamIsAnotherEvent() {
        final List<Event> events = List.of(event("a", 0, "21", "5"), event("b", 0, "22", "5"),
                event("c", 0, "21", "5"));

        final EventDedupe.Deduped deduped = EventDedupe.dedupe(events, SOURCES, id -> null);

        assertThat(deduped.events()).extracting(Event::id).containsExactly("a", "b");
        assertThat(deduped.duplicates()).containsExactlyEntriesOf(Map.of(0, 1));
    }

    @Test
    void eventsWithoutIdsAreAllKeptAndTheirSourceSaidNotDeduplicated() {
        final List<Event> events = List.of(event("a", 2, null, null), event("b", 2, null, null),
                event("c", 0, "x", "1"));

        final EventDedupe.Deduped deduped = EventDedupe.dedupe(events, SOURCES, null);

        assertThat(deduped.events()).extracting(Event::id).containsExactly("a", "b", "c");
        assertThat(deduped.duplicates()).isEmpty();
        assertThat(deduped.notDeduplicated()).containsExactly(2);
    }

    private static Event event(final String id, final int source, final String streamId, final String eventId) {
        return new Event(1_000L, source, id, Role.ACTION, "view", null, null, null, null, null, null, streamId,
                eventId);
    }

    private static Source source(final String name, final boolean streamIds) {
        return new Source(name, new DocRef(ElasticIndexDoc.TYPE, name + "-uuid", name), "UserId",
                JoinKeys.Kind.TEXT, List.<Transform>of(), null, null, null, null, null, null, streamIds,
                List.of(new RoleRead(Role.ACTION, null, null)), true, List.of(), List.of("pat"),
                PersonScope.By.LOGON, List.of(), Map.of());
    }
}
