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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.ai.impl.tools.timeline.TimelineFields.Origin;
import stroom.ai.impl.tools.timeline.TimelineReading.XmlPaths;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimelineReadNotes {

    private static final DocRef INDEX = new DocRef(ElasticIndexDoc.TYPE, "index-uuid", "Endpoint");

    private final TimelineFields fields = Mockito.mock(TimelineFields.class);
    private final ToolSession session = ToolSession.chat(1);

    @Test
    void aThingThePlansDifferOnWithNothingNamingItIsNotedAsTracedOrReadFromTheXml() {
        Mockito.when(fields.find(Mockito.any(), Mockito.eq(INDEX), Mockito.anyString(), Mockito.anyList(),
                Mockito.any())).thenReturn(Optional.empty());
        final Map<String, List<String>> byPipeline = new LinkedHashMap<>();
        byPipeline.put("HostName", List.of("V1"));
        byPipeline.put("host.name", List.of("V2"));
        Mockito.when(fields.find(Mockito.any(), Mockito.eq(INDEX), Mockito.eq(InstructionRules.HOST_FIELD),
                Mockito.anyList(), Mockito.any())).thenReturn(Optional.of(new Found("HostName", Origin.PLAN, null,
                null, byPipeline)));
        Mockito.when(fields.find(Mockito.any(), Mockito.eq(INDEX), Mockito.eq(InstructionRules.EVENT_TYPE_FIELD),
                Mockito.anyList(), Mockito.any())).thenReturn(Optional.of(new Found("TypeId", Origin.AGENTS, "/")));

        final List<String> notes = TimelineReadNotes.planned(fields, session, INDEX, Map.of(), true,
                field -> true);

        assertThat(notes).singleElement().asString().contains("'Endpoint'", "host in different fields (HostName, "
                                                                          + "host.name)",
                "traces every stream to its indexing pipeline", "EventSource/Device/HostName");
        // Named by the skill's source: nothing to note.
        assertThat(TimelineReadNotes.planned(fields, session, INDEX, Map.of(InstructionRules.HOST_FIELD,
                "HostName"), true, field -> true)).isEmpty();
    }

    @Test
    void noFieldHoldingAClassifiedSourcesTypesIsNotedButNotForASourceWithRoles() {
        Mockito.when(fields.find(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyList(),
                Mockito.any())).thenReturn(Optional.empty());

        assertThat(TimelineReadNotes.planned(fields, session, INDEX, Map.of(), true, field -> true))
                .singleElement().asString().contains("no field holds its events' type", "EventDetail/TypeId",
                        "a few events per code");
        assertThat(TimelineReadNotes.planned(fields, session, INDEX, Map.of(), false, field -> true)).isEmpty();
        assertThat(TimelineReadNotes.planned(fields, session, INDEX, Map.of(InstructionRules.EVENT_TYPE_FIELD,
                "code"), true, field -> true)).isEmpty();
    }

    @Test
    void whatRunsReadIsReadBackFromTheirResults() {
        final String first = "Activity timeline of pat...\n"
                             + "- Endpoint: user field UserId; its feeding pipelines' plans differ, so each event's "
                             + "own was read: host by HostName (" + StreamFields.BY_TASKS + ") 4, host.name ("
                             + StreamFields.BY_FILTERS + ") 2, EventSource/Device/HostName (" + StreamFields.BY_XML
                             + ") 3, " + StreamFields.NOT_READ + " 1\n"
                             + TimelineReadNotes.xmlLine(new XmlPaths("Endpoint", "Endpoint",
                ActivityTimelineTool.TYPE_PATHS, 5)) + "\n";
        final String second = TimelineReadNotes.xmlLine(new XmlPaths("Mail", "Mail index",
                ActivityTimelineTool.CONTENT_PATHS, 2));

        final TimelineReadNotes.Seen seen = TimelineReadNotes.seen(List.of(first, second, "Nothing read so."));

        assertThat(seen.runs()).isEqualTo(3);
        assertThat(seen.xmlEvents()).isEqualTo(7);
        assertThat(seen.pathsByIndex()).containsOnlyKeys("Endpoint", "Mail index");
        assertThat(seen.pathsByIndex().get("Endpoint")).isEqualTo(Set.copyOf(ActivityTimelineTool.TYPE_PATHS));
        assertThat(seen.traced()).isEqualTo(6);
        assertThat(seen.fromXml()).isEqualTo(3);
        assertThat(seen.notRead()).isEqualTo(1);
        assertThat(seen.any()).isTrue();
        assertThat(TimelineReadNotes.seen(List.of("Nothing read so.")).any()).isFalse();
    }
}
