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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.validation.EventChecks;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// check_events, the tool: the schema and quality checks together, the events of streams read by the tool, and
/// Events that pass recorded as validated. Ported from the Stroom MCP server's `tests/test_validation.py`,
/// `tests/test_formats.py` and `tests/test_surface.py` (the cases that call check_events; the checks themselves
/// are [EventChecks]'s, and reading the streams [EventStreams]', tested with them).
class TestCheckEventsTool {

    private static final String EVENT = """
            <?xml version="1.1" encoding="UTF-8"?><Events xmlns="event-logging:3"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="event-logging:3 file://event-logging-v9.9.9.xsd" Version="9.9.9"><Event>
            <EventTime><TimeCreated>2026-09-28T10:00:00.000Z</TimeCreated></EventTime>
            <EventSource><System><Name>ACME</Name><Environment>Dev</Environment></System><Generator>g</Generator>
            <Device><HostName>ws01</HostName></Device></EventSource>
            <EventDetail><TypeId>Logon</TypeId><Authenticate><Action>Logon</Action></Authenticate></EventDetail>
            </Event></Events>""";
    private static final String SYSTEM_ID = "file://event-logging-v9.9.9.xsd";
    private static final DocRef P = new DocRef(PipelineDoc.TYPE, "p", "Acme");

    private final BuildWorld world = new BuildWorld();
    private final EventChecks eventChecks = Mockito.mock(EventChecks.class);
    private final EventStreams eventStreams = Mockito.mock(EventStreams.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final BuildPlan plan = new BuildPlan(new FakeBuildFacts(), world.guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
    private final CheckEventsTool tool = new CheckEventsTool(eventChecks, eventStreams, streamReader,
            world.documentAccess, world.records(), plan);

    @Test
    void itTakesARunSlotAsTheOtherToolsThatReadStreamsDo() {
        // Before, it read and validated Events streams over /mcp without one, unlike summarise_streams.
        assertThat(tool.usesRunSlot()).isTrue();
    }

    @Test
    void eventsArePassedOnlyWhenTheyPassTheSchemaAndTheQualityRules() {
        valid(true, SYSTEM_ID);
        assertThat(call(Map.of("events_xml", EVENT)).get("ok")).isEqualTo(true);

        // Valid against the schema, but with no Device.
        final Map<String, Object> result = call(Map.of("events_xml", EVENT.replace(
                "<Device><HostName>ws01</HostName></Device>", "")));
        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(((Map<?, ?>) ((Map<?, ?>) result.get("quality")).get("rules")).keySet().stream()
                .map(String::valueOf)
                .toList()).containsExactly("device");
        assertThat(((Map<?, ?>) result.get("schema")).get("valid")).isEqualTo(true);
    }

    @Test
    void aRecordsSourceIsValidatedAsWhatItIsWithoutTheEventRules() {
        valid(true, "file://records-v2.0.xsd");

        final Map<String, Object> result = call(Map.of("events_xml",
                "<records xmlns=\"records:2\"><record/></records>"));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) result.get("quality")).get("note"))).contains("quality rules");
    }

    @Test
    void eventsSentHtmlEscapedAreReadUnescapedAndSaidSo() {
        valid(true, SYSTEM_ID);

        final Map<String, Object> result = call(Map.of("events_xml", EVENT.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("note")).isEqualTo(XsltChecks.ESCAPED);
    }

    @Test
    void checkEventsReadsTheEventsStreamsItself() {
        // Qwen in VS Code read 50 processed events back 23 at a time, 47 s a read, to send them to check_events.
        final Map<Long, Map<String, Object>> read = new LinkedHashMap<>();
        read.put(42L, Map.of("checked", 3, "records", 3));
        Mockito.when(eventStreams.read(List.of(42L), CheckEventsTool.MAX_RECORDS))
                .thenReturn(new EventStreams.Read(EVENT, read));
        Mockito.when(eventChecks.validateEvents(anyString(), any())).thenReturn(schemaResult(false, SYSTEM_ID, 1));

        final Map<String, Object> result = call(Map.of("stream_ids", List.of(42)));

        assertThat(result.get("read")).isEqualTo(Map.of("42", Map.of("checked", 3, "records", 3)));
        assertThat(((Map<?, ?>) result.get("schema")).get("valid")).isEqualTo(false);
        assertThat(((Map<?, ?>) result.get("schema")).get("error_count")).isEqualTo(1);
        assertThat(result).doesNotContainKey("recorded_for");
    }

    @Test
    void eventsXmlOrStreamIdsNotBoth() {
        for (final Map<String, Object> arguments : List.<Map<String, Object>>of(
                Map.of("events_xml", EVENT, "stream_ids", List.of(42)), Map.of())) {
            final ToolResult result = tool.execute(PortedToolCalls.call(CheckEventsTool.NAME,
                    JsonUtil.writeValueAsString(arguments, false)), PortedToolCalls.context());
            assertThat(result.isError()).isTrue();
            assertThat(result.text()).isEqualTo("Give events_xml (an <Events> document) or stream_ids (Events "
                                                + "streams), not both");
        }
    }

    @Test
    void eventsThatPassAreRecordedAsValidatedWithThePlansNextStep() {
        // The plan moves on from here too (seen in testing: the agent's checklist stayed on processing).
        PipelineLayers.given(world.pipelineService, P.getUuid(), PipelineLayers.of(PipelineLayers.layer(P.getUuid())
                .chain("xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender")));
        world.inBuild("acme-v1", PipelineDoc.TYPE, P.getName(), P.getUuid());
        streamsFrom(P.getUuid());
        valid(true, SYSTEM_ID);

        final Map<String, Object> result = call(Map.of("stream_ids", "42"));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("recorded_for")).isEqualTo(List.of("Acme"));
        assertThat(world.records().validated(P)).isTrue();
        assertThat(result).containsKey("next");
        assertThat(result.get("done")).isEqualTo(false);
    }

    @Test
    void eventsOfAPipelineOutsideABuildAreNotRecorded() {
        PipelineLayers.given(world.pipelineService, P.getUuid(), PipelineLayers.of(PipelineLayers.layer(P.getUuid())
                .chain("xsltFilter", "XSLTFilter")));
        world.add(PipelineDoc.TYPE, P.getName(), world.folder("System/Feeds").uuid, P.getUuid());
        streamsFrom(P.getUuid());
        valid(true, SYSTEM_ID);

        final Map<String, Object> result = call(Map.of("stream_ids", List.of(42)));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("recorded_for")).isEqualTo(List.of());
        assertThat(result).doesNotContainKey("next");
    }

    @Test
    void eventsThatCantBeCheckedAreRefusedSayingWhy() {
        Mockito.when(eventChecks.validateEvents(anyString(), any()))
                .thenThrow(new ToolRefusal("Events XML is not well-formed XML: oops"));

        final ToolResult result = tool.execute(PortedToolCalls.call(CheckEventsTool.NAME,
                JsonUtil.writeValueAsString(Map.of("events_xml", "<Events"), false)), PortedToolCalls.context());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).isEqualTo("Events XML is not well-formed XML: oops");
    }

    private void streamsFrom(final String pipelineUuid) {
        final Map<Long, Map<String, Object>> read = new LinkedHashMap<>();
        read.put(42L, Map.of("checked", 1, "records", 1));
        Mockito.when(eventStreams.read(List.of(42L), CheckEventsTool.MAX_RECORDS))
                .thenReturn(new EventStreams.Read(EVENT, read));
        Mockito.when(streamReader.metaRow(anyLong())).thenReturn(Optional.of(new MetaRow(Meta.builder().id(42)
                .typeName("Events").pipelineUuid(pipelineUuid).build(), null, Map.of())));
    }

    private void valid(final boolean valid, final String systemId) {
        Mockito.when(eventChecks.validateEvents(anyString(), any())).thenReturn(schemaResult(valid, systemId, 0));
    }

    private static Map<String, Object> schemaResult(final boolean valid, final String systemId, final int errors) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("schema", systemId);
        result.put("error_count", errors);
        result.put("errors", List.of());
        return result;
    }

    private Map<String, Object> call(final Map<String, Object> arguments) {
        return PortedToolCalls.json(tool.execute(PortedToolCalls.call(CheckEventsTool.NAME,
                JsonUtil.writeValueAsString(arguments, false)), PortedToolCalls.context()).text());
    }
}
