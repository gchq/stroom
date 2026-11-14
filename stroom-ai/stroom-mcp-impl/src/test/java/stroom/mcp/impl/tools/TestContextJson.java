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
import stroom.ai.impl.tools.generation.ReferenceGenerator;
import stroom.ai.impl.tools.generation.ReferenceMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.mcp.impl.tools.ContextLookups.Context;
import stroom.mcp.impl.tools.ContextLookups.Entry;
import stroom.mcp.impl.tools.ContextLookups.Key;
import stroom.mcp.impl.tools.ContextLookups.Part;
import stroom.mcp.impl.tools.ContextProposal.Output;
import stroom.mcp.impl.tools.ContextProposal.Preview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Context data that is JSON ([ContextJson]): its layouts read into entries, the key found by value matching as for
/// XML, previews of both shapes of mask table, and the pieces a proposal gives for it.
class TestContextJson {

    // An object wrapping its entries: two sources share id 7, source A has two versions of it, and an id 8. The ids
    // and versions are JSON numbers; the flags a table of {mask, name} objects, and also an object keyed by mask.
    private static final String WRAPPED_1 = """
            {"source": "test", "count": 3, "entries": [
              {"Source": "A", "Id": 7, "Version": 0, "Text": "A seven: %1", "Info": {"Step": "Start"},
               "Flags": [{"Mask": "0x1", "Name": "One"}, {"Mask": "0x8000000000000000", "Name": "Top"}],
               "Named": {"0x1": "One", "0x8000000000000000": "Top"}},
              {"Source": "A", "Id": 7, "Version": 1, "Text": "A seven v1: %2 then %1", "Info": {"Step": "Begin"},
               "Flags": [{"Mask": 2, "Name": "Two"}], "Named": {"2": "Two"}},
              {"Source": "B", "Id": 7, "Version": 0, "Text": "B seven", "Info": {"Step": "Stop"}, "Flags": [],
               "Named": {}, "Extra": null, "Args": ["a", "b"]},
              {"Source": "A", "Id": 8, "Version": 0, "Text": "A eight", "Info": {"Step": "Other"}, "Flags": [],
               "Named": {}}
            ]}""";
    // The same keys, A's version 0 worded differently.
    private static final String WRAPPED_2 = WRAPPED_1.replace("A seven: %1", "A's seven, again: %1");
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
            <Event><Sys><Src Name="A"/><Id>7</Id><Ver>0</Ver><Level>0</Level><Flags>0x8000000000000000</Flags></Sys>
              <Data><V>z</V></Data></Event>
            <Event><Sys><Src Name="C"/><Id>9</Id><Ver>0</Ver><Level>0</Level><Flags>0x1</Flags></Sys>
              <Data><V>w</V></Data></Event>
            </Events>""";

    @Test
    void anEntrysFieldsAreItsScalarsByDottedPathAndItsTablesBothShapes() {
        final Context context = ContextLookups.entries(WRAPPED_1, null);
        assertThat(context.json()).isTrue();
        assertThat(context.jsonLayout()).isEqualTo(ContextJson.WRAPPED);
        assertThat(context.path()).as("the longest array of objects").isEqualTo("entries");
        assertThat(context.describe()).contains("'entries' array");
        final Entry first = context.entries().getFirst();
        assertThat(first.fields()).containsExactly(Map.entry("Source", "A"), Map.entry("Id", "7"),
                Map.entry("Version", "0"), Map.entry("Text", "A seven: %1"), Map.entry("Info.Step", "Start"));
        assertThat(first.tables().get("Flags")).containsExactly(Map.of("Mask", "0x1", "Name", "One"),
                Map.of("Mask", "0x8000000000000000", "Name", "Top"));
        assertThat(first.tables().get("Named")).as("an object keyed by mask: each item its key and value")
                .containsExactly(Map.of("@key", "0x1", ".", "One"), Map.of("@key", "0x8000000000000000", ".", "Top"));
        assertThat(context.entries().get(1).tables().get("Flags").getFirst()).as("a JSON number as text")
                .containsEntry("Mask", "2");
        assertThat(context.entries().get(2).fields()).as("a null and an array of scalars are no fields")
                .doesNotContainKeys("Extra", "Args").containsKey("Info.Step");
        assertThat(context.entries().get(2).tables()).as("an empty table, an empty object")
                .doesNotContainKeys("Flags", "Named");
    }

    @Test
    void anArrayJsonLinesOneObjectAndANamedArrayAreEachRead() {
        final Context array = ContextLookups.entries("[{\"Id\": 1, \"T\": \"a\"}, {\"Id\": 2, \"T\": \"b\"}, 3]",
                null);
        assertThat(array.jsonLayout()).isEqualTo(ContextJson.ARRAY);
        assertThat(array.entries()).extracting(entry -> entry.fields().get("Id")).containsExactly("1", "2");
        assertThat(ContextJson.jsonLayout(array)).isEqualTo("array");
        assertThat(ContextJson.recordPath(array)).isNull();

        final Context lines = ContextLookups.entries("{\"Id\": 1}\n{\"Id\": 2}\n\n{\"Id\": 3}\n", null);
        assertThat(lines.jsonLayout()).isEqualTo(ContextJson.LINES);
        assertThat(lines.entries()).hasSize(3);
        assertThat(ContextJson.jsonLayout(lines)).isEqualTo("lines");

        final Context one = ContextLookups.entries("{\"Host\": \"ws01\", \"Site\": {\"Name\": \"North\"}}", null);
        assertThat(one.jsonLayout()).isEqualTo(ContextJson.OBJECT);
        assertThat(one.entries().getFirst().fields()).containsEntry("Site.Name", "North");

        final Context itself = ContextLookups.entries(WRAPPED_1, ".");
        assertThat(itself.jsonLayout()).as("'.' reads the object itself as the one entry")
                .isEqualTo(ContextJson.OBJECT);
        assertThat(itself.entries().getFirst().tables()).containsKey("entries");

        final Context nested = ContextLookups.entries("{\"meta\": {\"v\": 1}, \"data\": {\"events\": [{\"Id\": 1}, "
                                                      + "{\"Id\": 2}]}, \"other\": [{\"x\": 1}]}", null);
        assertThat(nested.path()).as("the longest array of objects, below an object").isEqualTo("data.events");
        assertThat(ContextJson.recordPath(nested)).isEqualTo("/map/map/map[@key = 'data']/array[@key = 'events']/map"
                                                             + " | /map/map[@key = 'data']/array[@key = 'events']"
                                                             + "/map");
        assertThat(ContextLookups.entries("{\"meta\": {\"v\": 1}, \"data\": {\"events\": [{\"Id\": 1}]}}",
                "data/events").path()).as("a path given with / as with .").isEqualTo("data.events");
    }

    @Test
    void jsonThatHoldsNoEntriesOrIsBrokenIsRefused() {
        assertThatThrownBy(() -> ContextLookups.entries("{\"a\": ", null)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("isn't JSON");
        assertThatThrownBy(() -> ContextLookups.entries("[1, 2]", null)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("holds no JSON objects");
        assertThatThrownBy(() -> ContextLookups.entries("[]", null)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("holds no entries");
        assertThatThrownBy(() -> ContextLookups.entries(WRAPPED_1, "missing")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("no array of entries at 'missing'");
        assertThatThrownBy(() -> ContextLookups.entries("[{\"a\": 1}]", "a")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("leave entries out");
        assertThatThrownBy(() -> ContextLookups.entries("not data", null)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("or JSON");
    }

    @Test
    void theKeyIsFoundByValuesAsForXml() {
        final Key key = ContextLookups.keys(parts(), null, null).getFirst();
        assertThat(key.contextFields()).containsExactlyInAnyOrder("Source", "Id", "Version");
        assertThat(key.eventFields()).as("JSON numbers matched as the records' text")
                .containsExactlyInAnyOrder("Sys/Src/@Name", "Sys/Id", "Sys/Ver");
        assertThat(key.matched()).as("all but the record of a source no context has").isEqualTo(4);
        assertThat(key.records()).isEqualTo(5);
    }

    @Test
    void bothShapesOfMaskTableArePreviewedAndEachPartReadsItsOwnContext() {
        final List<Part> parts = parts();
        final Key key = ContextLookups.keys(parts, null, null).getFirst();
        final List<Output> outputs = ContextProposal.outputs(List.of(
                Map.of("path", "EventDetail/Description", "from", "Text", "render", "fill_template",
                        "values", "Data/V"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Flag", "from", "Flags",
                        "render", "mask_names", "mask", "Sys/Flags", "mask_field", "Mask", "name_field", "Name",
                        "repeat", true),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Named", "from", "Named",
                        "render", "mask_names", "mask", "Sys/Flags")), parts);
        assertThat(outputs.get(2).maskField()).as("a table keyed by mask needs no item paths").isEqualTo("@key");
        assertThat(outputs.get(2).nameField()).isEqualTo(".");
        assertThat(preview(parts.get(0), key, 0, outputs.get(0)).values()).containsExactly("A seven: x");
        assertThat(preview(parts.get(1), key, 1, outputs.get(0)).values()).containsExactly("A's seven, again: z");
        assertThat(preview(parts.get(1), key, 0, outputs.get(0)).values()).containsExactly("A seven v1: q then p");
        assertThat(preview(parts.get(0), key, 0, outputs.get(1)).values()).containsExactly("One", "Top");
        assertThat(preview(parts.get(0), key, 0, outputs.get(2)).values()).containsExactly("One, Top");
        assertThat(preview(parts.get(1), key, 1, outputs.get(1)).values()).as("bit 63 alone")
                .containsExactly("Top");
        assertThat(preview(parts.get(1), key, 1, outputs.get(2)).values()).containsExactly("Top");
        assertThat(preview(parts.get(1), key, 0, outputs.get(2)).values()).as("a decimal key")
                .containsExactly("Two");
        final Entry b = ContextLookups.matched(parts.get(0), key, parts.get(0).records().get(1));
        final Preview unnamed = ContextProposal.preview(outputs.get(1), b, Map.of("Sys/Flags", List.of("0x5")));
        assertThat(unnamed.said()).as("an empty table names no bit").containsExactly("no name for bit 0 (0x1)",
                "no name for bit 2 (0x4)");
        assertThatThrownBy(() -> ContextProposal.outputs(List.of(Map.of("path", "x", "from", "Flags",
                "render", "mask_names", "mask", "Sys/Flags")), parts)).as("an array's items need their paths")
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("mask_field");
    }

    @Test
    void theConfirmedPiecesReadJsonAsStroomWritesIt() {
        final List<Part> parts = parts();
        final Key key = ContextLookups.keys(parts, List.of("Source", "Id", "Version"),
                List.of("Sys/Src/@Name", "Sys/Id", "Sys/Ver")).getFirst();
        final List<Output> outputs = ContextProposal.outputs(List.of(
                Map.of("path", "EventDetail/Description", "from", "Text", "render", "fill_template",
                        "values", "Data/V"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Step", "from", "Info.Step"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Flag", "from", "Flags",
                        "render", "mask_names", "mask", "Sys/Flags", "mask_field", "Mask", "name_field", "Name",
                        "repeat", true),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "Named", "from", "Named",
                        "render", "mask_names", "mask", "Sys/Flags")), parts);
        final Context context = ContextLookups.entries(WRAPPED_1, null);
        final Map<String, Object> reference = ContextProposal.referenceMapping("M", context, key, outputs);
        assertThat(reference).containsEntry("input", "json").containsEntry("json_layout", "lines")
                .containsEntry("record", "/map/map/array[@key = 'entries']/map | /map/array[@key = 'entries']/map")
                .doesNotContainKey("xml_namespace");
        @SuppressWarnings("unchecked") final List<Map<String, Object>> values = (List<Map<String, Object>>)
                ((List<Map<String, Object>>) reference.get("maps")).getFirst().get("values");
        assertThat(values).containsExactly(
                Map.of("element", "text", "field", "Text"),
                Map.of("element", "info_step", "field", "Info.Step"),
                Map.of("element", "flags", "xpath", "*[@key='Flags']/*", "copy", true),
                Map.of("element", "named", "xpath", "*[@key='Named']/*", "copy", true));
        final ReferenceGenerator.Generated loader = ReferenceGenerator.generate(ReferenceMapping.fromJson(reference));
        assertThat(loader.ok()).as(String.valueOf(loader.problems())).isTrue();
        assertThat(loader.xslt()).contains("xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\"",
                "*[@key='Info']/*[@key='Step']", "*[@key='Source']", "mode=\"plain-copy\"");

        final List<Map<String, Object>> entries = ContextProposal.mappingEntries("M", context, key, outputs);
        assertThat(entries.get(1)).containsEntry("lookup", Map.of("map", "M", "key_fields", key.eventFields(),
                "path", "info_step"));
        assertThat(entries.get(2)).containsEntry("mask_names", Map.of("lookup", Map.of("map", "M",
                "key_fields", key.eventFields(), "path", "flags/*"), "mask", "Mask", "name", "Name"));
        assertThat(entries.get(3)).containsEntry("mask_names", Map.of("lookup", Map.of("map", "M",
                "key_fields", key.eventFields(), "path", "named/*"), "mask", "@key", "name", "."));
        final TranslationMapping mapping = TranslationMapping.fromJson(Map.of("input", "xml", "root", "Events",
                "record", "Event", "events", List.of(Map.of("name", "all", "allow_unknown", "context test",
                        "fields", entries))));
        assertThat(mapping.allEntries()).hasSize(4);

        final Context array = ContextLookups.entries("[{\"Id\": 1, \"T\": \"a\"}]", null);
        assertThat(ContextProposal.referenceMapping("M", array, new Key(List.of("Id"), List.of("Sys/Id"), 1, 1),
                List.of())).containsEntry("json_layout", "array").doesNotContainKey("record");
        assertThat(ContextJson.itemPath("Info.Mask")).isEqualTo("Info/Mask");
    }

    private static Preview preview(final Part part, final Key key, final int record, final Output output) {
        final Map<String, List<String>> fields = part.records().get(record);
        return ContextProposal.preview(output, ContextLookups.matched(part, key, fields), fields);
    }

    private static List<Part> parts() {
        return List.of(
                new Part(1, 0, ContextLookups.entries(WRAPPED_1, null).entries(), ContextLookups.records(EVENTS_1,
                        100)),
                new Part(1, 1, ContextLookups.entries(WRAPPED_2, null).entries(), ContextLookups.records(EVENTS_2,
                        100)));
    }
}
