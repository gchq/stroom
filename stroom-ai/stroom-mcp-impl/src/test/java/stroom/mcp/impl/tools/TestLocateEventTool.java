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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.shared.AiToolCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestLocateEventTool {

    private static final String STORED = "<Events xmlns=\"event-logging:3\"><Event><EventSource><User><Id>b</Id>"
                                         + "</User></EventSource></Event></Events>";

    private final TestEventLocations world = new TestEventLocations();
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private LocateEventTool tool;

    @BeforeEach
    void setUp() {
        world.setUp();
        tool = new LocateEventTool(world.eventLocations, world.streamReader, world.steppingWalks,
                world.documentAccess, auditLog);
        Mockito.when(world.documentAccess.canViewData()).thenReturn(true);
        Mockito.when(world.streamReader.fetch(Mockito.eq(6L), Mockito.anyLong(), Mockito.any()))
                .thenReturn(new StreamReader.Fetched("ACME", "Raw Events", "raw", false, 1L, null, List.of()));
        Mockito.when(world.streamReader.fetch(Mockito.eq(8L), Mockito.anyLong(), Mockito.any()))
                .thenReturn(new StreamReader.Fetched("ACME", "Events", STORED, true, 3L, null, List.of()));
    }

    @Test
    void aStreamIsTracedBackToItsRawStreamPipelineAndCode() {
        final Map<String, Object> result = tool.locate(9, null, null, new ArrayList<>());

        assertThat(result.get("raw_stream")).isEqualTo(6L);
        assertThat(result.get("events_stream")).isEqualTo(8L);
        assertThat(result.get("feed")).isEqualTo("ACME");
        assertThat(result.get("raw_parts")).isEqualTo(1L);
        assertThat(result.get("pipeline")).isEqualTo(Map.of("uuid", "p", "name", "Acme-Events"));
        assertThat((String) result.get("hint")).startsWith("Give event_id to pin down the record");
    }

    @Test
    void anEventIsShownStoredBesideWhatSteppingItsRecordGivesNow() {
        world.steps(TestEventXml.OUTPUT.replace("<Id>b</Id>", "<Id>x</Id>"), "<Events/>",
                "<Events xmlns=\"event-logging:3\"><Event><EventSource><User><Id>b</Id></User></EventSource>"
                + "</Event></Events>");

        final Map<String, Object> result = tool.locate(8, 3, null, new ArrayList<>());

        assertThat(result.get("location")).isEqualTo(Map.of("part", 0L, "record", 3L, "event_within_record", 1,
                "events_from_record", 1));
        assertThat(result.get("record_input")).isEqualTo("<record 3/>");
        assertThat(result.get("same_as_stored")).isEqualTo(true);
        assertThat(result).doesNotContainKey("note");
        assertThat((String) result.get("stepped_event_now")).contains("<Id>b</Id>");
    }

    @Test
    void anEventSteppedDifferentlyNowSaysTheTranslationChanged() {
        world.steps("<Events xmlns=\"event-logging:3\"><Event><EventSource><User><Id>changed</Id></User>"
                    + "</EventSource></Event></Events>");

        final Map<String, Object> result = tool.locate(8, 1, null, new ArrayList<>());

        assertThat(result.get("same_as_stored")).isEqualTo(false);
        assertThat((String) result.get("note")).contains("changed since it was processed");
    }

    @Test
    void anEventPastTheEndOfItsStreamIsRefused() {
        assertThat(text("{\"stream_id\": 8, \"event_id\": 4}"))
                .isEqualTo("Error: Events stream 8 has 3 events; there is no event 4");
    }

    @Test
    void badArgumentsAndMissingPermissionsAreRefused() {
        assertThat(text("{}")).isEqualTo("Error: give stream_id, the stream the user reported.");
        assertThat(text("{\"stream_id\": 8, \"event_id\": 0}")).contains("counts from 1");
        Mockito.when(world.documentAccess.canViewData()).thenReturn(false);
        assertThat(text("{\"stream_id\": 8}")).contains("don't have permission to view data");
    }

    @Test
    void theResultIsJsonAndTheStreamsReadAreLogged() {
        final Map<String, Object> result = BuildToolCalls.json(text("{\"stream_id\": 9}"));

        assertThat(result.get("raw_stream")).isEqualTo(6);
        Mockito.verify(auditLog).streamsViewed(Mockito.any(), Mockito.eq(List.of(6L)), Mockito.anyString());
    }

    @Test
    void itIsAHeavyProcessingTool() {
        assertThat(tool.category()).isEqualTo(AiToolCategory.PROCESSING);
        assertThat(tool.usesRunSlot()).isTrue();
    }

    private String text(final String arguments) {
        return tool.execute(BuildToolCalls.call(LocateEventTool.NAME, arguments), BuildToolCalls.context()).text();
    }
}
