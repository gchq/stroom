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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.ReferenceMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.mcp.impl.tools.ContextLookups.Key;
import stroom.mcp.impl.tools.ContextLookups.Part;
import stroom.mcp.impl.tools.ContextProposal.Output;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The key a context lookup is found by, from value sets ([ContextLookups]), and what a proposal makes of it
/// ([ContextProposal]): each part's records matched against that part's own context.
class TestContextLookups {

    // Two sources share id 7; source A has two versions of it, and an id 8.
    private static final String CONTEXT_1 = """
            <Meta>
              <Entry Source="A" Id="7" Version="0"><Text>A seven: %1</Text><Step>Start</Step>
                <Flags><Flag Mask="0x1" Name="One"/><Flag Mask="0x8000000000000000" Name="Top"/></Flags></Entry>
              <Entry Source="A" Id="7" Version="1"><Text>A seven v1: %2 then %1</Text><Step>Begin</Step>
                <Flags><Flag Mask="0x2" Name="Two"/></Flags></Entry>
              <Entry Source="B" Id="7" Version="0"><Text>B seven {1}</Text><Step>Stop</Step><Flags/></Entry>
              <Entry Source="A" Id="8" Version="0"><Text>A eight</Text><Step>Other</Step><Flags/></Entry>
            </Meta>""";
    // The same keys, A's version 0 worded differently.
    private static final String CONTEXT_2 = CONTEXT_1.replace("A seven: %1", "A's seven, again: %1");
    private static final String EVENTS_1 = """
            <Events>
            <Event><Sys><Src Name="A"/><Id>7</Id><Ver>0</Ver><Level>0</Level><Flags>0x8000000000000001</Flags></Sys>
              <Data><V>x</V></Data></Event>
            <Event><Sys><Src Name="B"/><Id>7</Id><Ver>0</Ver><Level>4</Level><Flags>0</Flags></Sys>
              <Data><V>y</V></Data></Event>
            </Events>""";
    private static final String EVENTS_2 = """
            <Events>
            <Event><Sys><Src Name="A"/><Id>7</Id><Ver>1</Ver><Level>0</Level><Flags>0x2</Flags></Sys>
              <Data><V>p</V><V>q</V></Data></Event>
            <Event><Sys><Src Name="A"/><Id>7</Id><Ver>0</Ver><Level>0</Level><Flags>0x1</Flags></Sys>
              <Data><V>z</V></Data></Event>
            <Event><Sys><Src Name="C"/><Id>9</Id><Ver>0</Ver><Level>0</Level><Flags>0x1</Flags></Sys>
              <Data><V>w</V></Data></Event>
            </Events>""";

    @Test
    void theKeyIsTheFewestFieldsThatPickOutOneEntryAndItsRecordFieldsAreFoundByTheirValues() {
        final List<Part> parts = parts();
        final List<Key> keys = ContextLookups.keys(parts, null, null);
        assertThat(keys).isNotEmpty();
        final Key key = keys.getFirst();
        assertThat(key.contextFields()).containsExactlyInAnyOrder("@Source", "@Id", "@Version");
        assertThat(key.eventFields()).as("Ver, not Level, whose values are among the versions too")
                .containsExactlyInAnyOrder("Sys/Src/@Name", "Sys/Id", "Sys/Ver");
        assertThat(key.records()).isEqualTo(5);
        assertThat(key.matched()).as("all but the record of a source no context has").isEqualTo(4);
        assertThat(key.percent()).isEqualTo(80);
    }

    @Test
    void aKeyGivenIsCountedAsItIs() {
        final Key key = ContextLookups.keys(parts(), List.of("@Source", "@Id"), List.of("Sys/Src/@Name", "Sys/Id"))
                .getFirst();
        assertThat(key.matched()).as("source A's id 7 picks out two entries: none").isEqualTo(1);
        assertThatThrownBy(() -> ContextLookups.keys(parts(), List.of("@Source"), List.of("a", "b")))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("same order");
    }

    @Test
    void eachPartsRecordsReadItsOwnContext() {
        final List<Part> parts = parts();
        final Key key = ContextLookups.keys(parts, null, null).getFirst();
        final List<Output> outputs = ContextProposal.outputs(List.of(Map.of("path", "EventDetail/Description",
                "from", "Text", "render", "fill_template", "values", "Data/V")), parts);
        assertThat(outputs.getFirst().pattern()).as("the commonest placeholder in the templates").isEqualTo(
                "%(\\d+)");
        final Output description = outputs.getFirst();
        assertThat(preview(parts.get(0), key, 0, description)).containsExactly("A seven: x");
        assertThat(preview(parts.get(1), key, 1, description)).containsExactly("A's seven, again: z");
        assertThat(preview(parts.get(1), key, 0, description)).as("version 1's template")
                .containsExactly("A seven v1: q then p");
    }

    @Test
    void aMaskIsNamedFromTheEntrysTable() {
        final List<Part> parts = parts();
        final Key key = ContextLookups.keys(parts, null, null).getFirst();
        final Output flags = ContextProposal.outputs(List.of(Map.of("path", "EventDetail/Unknown/Data",
                "data_name", "Flag", "from", "Flags/Flag", "render", "mask_names", "mask", "Sys/Flags",
                "mask_field", "@Mask", "name_field", "@Name", "repeat", true)), parts).getFirst();
        final ContextProposal.Preview preview = ContextProposal.preview(flags, ContextLookups.matched(parts.get(0),
                key, parts.get(0).records().get(0)), parts.get(0).records().get(0));
        assertThat(preview.input()).isEqualTo("0x8000000000000001");
        assertThat(preview.values()).containsExactly("One", "Top");
        assertThat(preview.said()).isEmpty();
        final ContextProposal.Preview unnamed = ContextProposal.preview(flags, ContextLookups.matched(parts.get(1),
                key, parts.get(1).records().get(1)), Map.of("Sys/Flags", List.of("0x5")));
        assertThat(unnamed.said()).containsExactly("no name for bit 2 (0x4)");
    }

    @Test
    void theConfirmedPiecesAreKeyedAlikeAndRead() {
        final List<Part> parts = parts();
        final Key key = ContextLookups.keys(parts, null, null).getFirst();
        final List<Output> outputs = ContextProposal.outputs(List.of(
                Map.of("path", "EventDetail/Description", "from", "Text", "render", "fill_template",
                        "values", "Data/V"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Flag", "from", "Flags/Flag",
                        "render", "mask_names", "mask", "Sys/Flags", "mask_field", "@Mask", "name_field", "@Name",
                        "repeat", true),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Step", "from", "Step")), parts);
        final ContextLookups.Context context = ContextLookups.entries(CONTEXT_1, null);
        final Map<String, Object> reference = ContextProposal.referenceMapping("M", context, key, outputs);
        assertThat(reference).containsEntry("input", "xml").containsEntry("record", "*/Entry");
        final ReferenceMapping read = ReferenceMapping.fromJson(reference);
        assertThat(read.maps().getFirst().keyFields()).isEqualTo(key.contextFields());
        assertThat(read.maps().getFirst().values()).extracting(value -> value.element())
                .containsExactly("text", "flag", "step");
        assertThat(read.maps().getFirst().values().get(1).copy()).isTrue();

        final List<Map<String, Object>> entries = ContextProposal.mappingEntries("M", key, outputs);
        assertThat(entries.get(0)).containsEntry("lookup", Map.of("map", "M", "key_fields", key.eventFields(),
                "path", "text")).containsEntry("fill_template", Map.of("pattern", "%(\\d+)", "field", "Data/V"));
        assertThat(entries.get(1)).containsEntry("field", "Sys/Flags").containsEntry("repeat", true)
                .containsEntry("mask_names", Map.of("lookup", Map.of("map", "M", "key_fields", key.eventFields(),
                        "path", "flag/Flag"), "mask", "@Mask", "name", "@Name"));
        final TranslationMapping mapping = TranslationMapping.fromJson(Map.of("input", "xml", "root", "Events",
                "record", "Event", "events", List.of(Map.of("name", "all", "allow_unknown", "context test",
                        "fields", entries))));
        assertThat(mapping.allEntries()).hasSize(3);
    }

    @Test
    void contextThatIsNoXmlOrHasNoEntriesIsRefused() {
        assertThatThrownBy(() -> ContextLookups.entries("a=b", null)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("isn't one XML document");
        assertThatThrownBy(() -> ContextLookups.entries(CONTEXT_1, "Missing")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("no entries at Meta/Missing");
        assertThat(ContextLookups.entries(CONTEXT_1, "/Meta/Entry").entries()).hasSize(4);
        assertThatThrownBy(() -> ContextProposal.outputs(List.of(Map.of("path", "x", "from", "Text",
                "render", "mask_names")), parts())).isInstanceOf(ToolRefusal.class).hasMessageContaining("mask_field");
    }

    private static List<String> preview(final Part part, final Key key, final int record, final Output output) {
        final Map<String, List<String>> fields = part.records().get(record);
        return ContextProposal.preview(output, ContextLookups.matched(part, key, fields), fields).values();
    }

    private static List<Part> parts() {
        return List.of(
                new Part(1, 0, ContextLookups.entries(CONTEXT_1, null).entries(), ContextLookups.records(EVENTS_1,
                        100)),
                new Part(1, 1, ContextLookups.entries(CONTEXT_2, null).entries(), ContextLookups.records(EVENTS_2,
                        100)));
    }
}
