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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.textconverter.TextConverterStore;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Sample profiling, and the fragment wrapper an environment uses (the Stroom MCP server's
/// `tests/test_xml_fragments.py` cases for `with_fragment_setup`).
class TestSampleProfiling {

    static final String FRAGMENTS = """
            <Event><System><TimeCreated SystemTime="2026-10-01T10:00:00.000Z"/><Computer>ws01</Computer>\
            <EventID>4624</EventID></System><EventData><Data Name="TargetUserName">alice</Data></EventData></Event>
            <Event><System><TimeCreated SystemTime="2026-10-01T10:05:00.000Z"/><Computer>ws02</Computer>\
            <EventID>4625</EventID></System><EventData><Data Name="TargetUserName">bob</Data></EventData></Event>
            <Event><System><TimeCreated SystemTime="2026-10-01T10:30:00.000Z"/><Computer>ws01</Computer>\
            <EventID>4634</EventID></System><EventData><Data Name="TargetUserName">alice</Data></EventData></Event>
            """;
    static final String LIVE_WRAPPER = """
            <?xml version="1.1" encoding="UTF-8"?>
            <!DOCTYPE records [<!ENTITY fragment SYSTEM "fragment">]>
            <Events
              xmlns="event-logging:3"
              xmlns:stroom="stroom"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="event-logging:3 file://event-logging-v3.4.2.xsd"
              Version="3.4.2">
              &fragment;
            </Events>

            <!--<records xmlns="records:2">-->
            <!--&fragment;-->
            <!--</records>-->
            """;
    static final String EVENTS = ("<Event><EventTime><TimeCreated>2026-10-01T10:00:00.000Z</TimeCreated></EventTime>"
                                  + "<EventSource><System><Name>App</Name><Environment>Prod</Environment></System>"
                                  + "</EventSource><EventDetail><TypeId>1</TypeId></EventDetail></Event>").repeat(2);

    private final TextConverterStore store = Mockito.mock(TextConverterStore.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final List<DocRef> converters = new ArrayList<>();

    private final SampleProfiling profiling = new SampleProfiling.Profiled();

    @Test
    void aSampleIsProfiledByThePortedProfiler() {
        final Map<String, Object> profiled = profiling.profile("""
                {"user": "alice", "action": "login"}
                {"user": "bob", "action": "logout"}
                """);

        assertThat(profiled).containsEntry("format", "json lines");
    }

    @Test
    void severalSamplesAreProfiledTogether() {
        final Map<String, Object> profiled = profiling.profileMany(Map.of(
                "a.log", "{\"user\": \"alice\"}\n",
                "b.log", "{\"user\": \"bob\"}\n"));

        assertThat(profiled).containsKey("files");
    }

    @Test
    void fragmentsFollowTheEnvironmentsOwnWrapper() {
        // Seen on live: every fragment wrapper is an <Events> one in event-logging:3; the server proposed records:2.
        converter("w1", "Event Logging v3.4.2 Fragments", "/Format Handling", TextConverterType.XML_FRAGMENT,
                LIVE_WRAPPER);
        converter("ds", "CSV", "/Feeds", TextConverterType.DATA_SPLITTER, "<dataSplitter/>");
        converter("own", "Mine", "/MCP Workspace/b", TextConverterType.XML_FRAGMENT,
                SampleProfiler.XML_FRAGMENT_WRAPPER);
        // The find can name a document since deleted.
        converters.add(new DocRef(TextConverterDoc.TYPE, "gone", "Deleted"));

        final Map<String, Object> setup = environment().withFragmentSetup(SampleProfiler.profile(FRAGMENTS));

        // Saved as the environment's own, tidied: its commented-out older version left behind.
        final Map<?, ?> converter = (Map<?, ?>) setup.get("text_converter");
        assertThat(converter.get("code")).isEqualTo(SampleProfiler.cleanWrapper(LIVE_WRAPPER));
        assertThat(SampleProfiler.cleanWrapper(LIVE_WRAPPER)).doesNotContain("<!--");
        assertThat(converter.get("environment")).isEqualTo(Map.of("name", "Event Logging v3.4.2 Fragments",
                "uuid", "w1", "path", "/Format Handling"));
        assertThat(((Map<?, ?>) setup.get("xslt_input")).get("mapping")).isEqualTo(Map.of("input", "xml_fragments",
                "xml_namespace", "event-logging:3", "record", "Event"));
        // The build's own converters (in the workspace) are not the environment's convention.
        assertThat(converter.containsKey("other_wrappers")).isFalse();

        // With none in the environment, the standard records:2 one.
        converters.clear();
        final Map<String, Object> plain = environment().withFragmentSetup(SampleProfiler.profile(FRAGMENTS));
        assertThat(((Map<?, ?>) plain.get("text_converter")).get("code"))
                .isEqualTo(SampleProfiler.XML_FRAGMENT_WRAPPER);
        assertThat(((Map<?, ?>) plain.get("xslt_input")).get("namespace")).isEqualTo("records:2");
    }

    @Test
    void eventLoggingFragmentsTakeAnEventsWrapperInTheChosenVersionElseTheNewest() {
        Mockito.when(heldSchemas.chosenOrLatest(null)).thenReturn("4.1.0");
        Mockito.when(heldSchemas.chosenOrLatest("3.5.2")).thenReturn("3.5.2");
        final Map<String, Object> profiled = SampleProfiler.profile(EVENTS);

        final Map<String, Object> setup = environment().withFragmentSetup(profiled);

        // The newest this Stroom holds.
        assertThat((String) ((Map<?, ?>) setup.get("text_converter")).get("code"))
                .contains("event-logging-v4.1.0.xsd");
        // The user's choice for the build.
        assertThat((String) ((Map<?, ?>) environment().withFragmentSetup(profiled, "3.5.2").get("text_converter"))
                .get("code")).contains("event-logging-v3.5.2.xsd");
        assertThat(((Map<?, ?>) setup.get("xslt_input")).get("namespace")).isEqualTo("event-logging:3");
    }

    @Test
    void otherFormatsAreLeftAsTheyAre() {
        final Map<String, Object> profiled = SampleProfiler.profile("a=1 b=2\na=3 b=4\n");

        assertThat(environment().withFragmentSetup(profiled)).isSameAs(profiled);
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void aSampleTheProfilerRefusesIsAToolRefusal() {
        // Text that starts as JSON and isn't.
        assertThatThrownBy(() -> profiling.profile("{\"user\": "))
                .isInstanceOf(ToolRefusal.class);
    }

    private SampleProfiling environment() {
        Mockito.when(store.list()).thenReturn(converters);
        Mockito.when(guard.workspaceFolder()).thenReturn("MCP Workspace");
        return new SampleProfiling.Profiled(new FragmentWrappers(() -> store, documentAccess, () -> guard,
                heldSchemas));
    }

    private void converter(final String uuid,
                           final String name,
                           final String path,
                           final TextConverterType type,
                           final String code) {
        final DocRef ref = new DocRef(TextConverterDoc.TYPE, uuid, name);
        converters.add(ref);
        final TextConverterDoc doc = TextConverterDoc.builder().uuid(uuid).name(name).converterType(type).data(code)
                .build();
        Mockito.when(documentAccess.read(ref, TextConverterDoc.class)).thenReturn(Optional.of(doc));
        Mockito.when(documentAccess.path(ref)).thenReturn(path);
    }
}
