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
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.sequence.JoinKeys;
import stroom.ai.impl.tools.timeline.TimelineReader.RoleRead;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineReader.Varied;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Each event's value of a thing the pipelines feeding its index put in different fields: its own stream's
/// pipeline's field, else the field it holds.
class TestStreamFields {

    private static final DocRef INDEX = new DocRef(ElasticIndexDoc.TYPE, "index-uuid", "Endpoint");
    private static final DocRef OLD = new DocRef("Pipeline", "old-uuid", "Endpoint indexing V1");
    private static final DocRef NEW = new DocRef("Pipeline", "new-uuid", "Endpoint indexing V2");

    @Test
    void eachEventTakesItsOwnStreamsPipelinesFieldElseTheOneItHolds() {
        final Source source = source(Map.of(InstructionRules.HOST_FIELD, new Varied("HostName", Map.of(
                OLD.getName(), "HostName", NEW.getName(), "host.name")), InstructionRules.EVENT_TYPE_FIELD,
                new Varied("TypeId", Map.of(OLD.getName(), "TypeId", NEW.getName(), "event.code"))));
        final Map<String, Map<String, String>> extras = new LinkedHashMap<>();
        // The new pipeline's document also holds HostName, meaning something else there: its own field is read.
        extras.put("a", Map.of("HostName", "ws-old", "TypeId", "logon"));
        extras.put("b", Map.of("HostName", "relay-01", "host.name", "ws-new", "event.code", "4624"));
        // A stream not seen (no provenance): the plans' field it holds.
        extras.put("c", Map.of("host.name", "ws-other", "event.code", "4625"));
        extras.put("d", Map.of());
        final List<Event> events = List.of(event("a", "1", "ws-old"), event("b", "2", "relay-01"),
                event("c", "3", null), event("d", null, null));

        final StreamFields.Resolved resolved = StreamFields.resolve(events, List.of(source), extras,
                streamId -> streamId == null
                        ? null
                        : streamId == 1L
                                ? OLD
                                : streamId == 2L
                                        ? NEW
                                        : null);

        assertThat(resolved.events()).extracting(Event::host).containsExactly("ws-old", "ws-new", "ws-other", null);
        // The classification reads the event type under the primary field's name, whichever field held it.
        assertThat(resolved.extras().get("b")).containsEntry("TypeId", "4624");
        assertThat(resolved.extras().get("c")).containsEntry("TypeId", "4625");
        assertThat(resolved.extras().get("d")).doesNotContainKey("TypeId");
        assertThat(resolved.read().get(0).get(InstructionRules.HOST_FIELD)).containsExactly(
                Map.entry("HostName (" + StreamFields.BY_STREAM + ")", 1L),
                Map.entry("host.name (" + StreamFields.BY_STREAM + ")", 1L),
                Map.entry("host.name (" + StreamFields.BY_HELD + ")", 1L));
    }

    @Test
    void sourcesWhosePipelinesAgreeAreLeftAsTheyAre() {
        final List<Event> events = List.of(event("a", "1", "ws-01"));
        final Map<String, Map<String, String>> extras = Map.of("a", Map.of("HostName", "ws-01"));

        final StreamFields.Resolved resolved = StreamFields.resolve(events, List.of(source(Map.of())), extras,
                streamId -> OLD);

        assertThat(resolved.events()).isEqualTo(events);
        assertThat(resolved.read()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------

    private static Source source(final Map<String, Varied> varied) {
        return new Source("Endpoint", INDEX, "UserId", JoinKeys.Kind.TEXT, List.<Transform>of(), "HostName", null,
                null, null, null, null, true, List.of(new RoleRead(Role.ACTION, null, null)), true,
                List.of("HostName", "host.name", "TypeId", "event.code"), List.of("pat"), PersonScope.By.LOGON,
                List.of(), varied);
    }

    private static Event event(final String id, final String streamId, final String host) {
        return new Event(0, 0, id, Role.ACTION, null, host, null, null, null, null, null, streamId, "1");
    }
}
