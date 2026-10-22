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

import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// build_data_splitter (the Stroom MCP server's `tools/generation.py` `build_data_splitter`, and the tool parts of
/// `tests/test_generation.py` and `test_xml_fragments.py`; the spec, its inference and dry run are
/// [stroom.ai.impl.tools.sample.SplitterDryRun]'s, tested with it).
class TestBuildDataSplitterTool {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n"
                                       + "2026-10-01T10:05:00Z,bob,logout\n";

    private final TranslationWorld world = new TranslationWorld();
    private final BuildDataSplitterTool tool = new BuildDataSplitterTool(world.sampleStreamTexts,
            world.sampleProfiling, world.docs, world.eventVersions, world.plan);

    @Test
    void toolsReadTheSampleFromItsStreamsInsteadOfItsText() {
        // The text goes through the model once, to upload_sample; the tools after it take stream_ids.
        Mockito.when(world.sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", CSV), List.of()));

        final Map<String, Object> splitter = world.call(tool, Map.of("stream_ids", List.of(7)));

        assertThat(splitter).containsEntry("records", 2).containsEntry("inferred", true)
                .containsEntry("converter_type", "DATA_SPLITTER");
        assertThat(((Map<?, ?>) splitter.get("spec")).get("kind")).isEqualTo("delimited");
        assertThat((String) splitter.get("hint")).startsWith("Every line parsed.");
    }

    @Test
    void everySampleStreamMustParseNotJustTheFirst() {
        Mockito.when(world.sampleStreamTexts.read(List.of(7L, 8L))).thenReturn(new SampleStreamTexts.Texts(
                orderedOf("stream 7", CSV, "stream 8", "time,user,action\n2026-10-01T11:00:00Z,carol,login\n"
                                                       + "garbled line with no commas\n"), List.of()));

        final Map<String, Object> splitter = world.call(tool, Map.of("stream_ids", List.of(7, 8), "spec",
                Map.of("kind", "regex", "pattern", "^([^ ,]+),([^ ,]+),([^ ,]+)$", "names",
                        List.of("time", "user", "action")), "save_as", "ACME-SPLITTER", "build", "acme-v1"));

        // The header lines are records to a regex: 3 of each, and the garbled line in the second.
        assertThat(splitter.get("records")).isEqualTo(5);
        assertThat(splitter.get("unmatched_count")).isEqualTo(1);
        assertThat((String) splitter.get("not_saved")).contains("1 line(s) match nothing");
        assertThat(splitter).doesNotContainKey("saved");
    }

    @Test
    void saveAsSavesTheConverterInTheBuildWhenEveryLineParsed() {
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV, "save_as", "ACME-SPLITTER",
                "build", "acme-v1"));

        final Map<?, ?> saved = (Map<?, ?>) splitter.get("saved");
        assertThat(saved.get("type")).isEqualTo(TextConverterDoc.TYPE);
        final TextConverterDoc doc = (TextConverterDoc) world.world.docs.get(String.valueOf(saved.get("uuid")));
        assertThat(doc.getConverterType()).isEqualTo(TextConverterType.DATA_SPLITTER);
        assertThat(doc.getData()).isEqualTo(splitter.get("converter"));
        // Made in the build's Events folder, until a pipeline writing Records runs it.
        assertThat(world.world.guard().folderContents("acme-v1")).anySatisfy(found ->
                assertThat(found.role()).isEqualTo("Events"));
    }

    @Test
    void jsonAndXmlNeedNoDataSplitter() {
        final Map<String, Object> refused = world.call(tool, Map.of("sample",
                "{\"user\": \"alice\"}\n{\"user\": \"bob\"}\n"));

        assertThat((String) refused.get("error")).startsWith("The sample is json lines: it needs no Data Splitter.")
                .endsWith("Go on to build_translation_xslt (input json).");
    }

    @Test
    void aSpecThatIsNotOneSaysWhatOneLooksLike() {
        final Map<String, Object> refused = world.call(tool, Map.of("sample", CSV, "spec", Map.of("kind", "nope")));

        assertThat((String) refused.get("error")).startsWith("spec is not a splitter spec (")
                .contains("For delimited data it looks like {'kind': 'delimited'")
                .endsWith("or leave spec out and it is inferred from the sample.");
    }

    @Test
    void neitherSampleNorSpecIsRefused() {
        assertThat(world.call(tool, Map.of())).containsEntry("error", "Give the sample (the file's text, or "
                                                                      + "stream_ids): the spec is inferred from it");
    }

    @Test
    void theConverterStepGivesFragmentsTheirWrapperRatherThanSkippingIt() {
        // Seen: the plan's converter step called build_data_splitter, which said "no Data Splitter", and none was
        // saved.
        final FragmentWrappers wrappers = Mockito.mock(FragmentWrappers.class);
        final Map<String, Object> live = new LinkedHashMap<>();
        live.put("name", "Event Logging v3.4.2 Fragments");
        live.put("uuid", "w1");
        live.put("path", "/Format Handling");
        live.put("code", TestSampleProfiling.LIVE_WRAPPER);
        live.put("root", "Events");
        live.put("namespace", "event-logging:3");
        Mockito.when(wrappers.find()).thenReturn(List.of(live));
        Mockito.when(wrappers.version(any())).thenReturn(TranslationWorld.VERSION);
        final BuildDataSplitterTool environment = new BuildDataSplitterTool(world.sampleStreamTexts,
                new SampleProfiling.Profiled(wrappers), world.docs, world.eventVersions, world.plan);

        final Map<String, Object> result = world.call(environment, Map.of("sample", TestSampleProfiling.FRAGMENTS,
                "save_as", "ACME-WRAPPER", "build", "acme-v1"));

        assertThat(result).containsEntry("converter_type", "XML_FRAGMENT")
                .containsEntry("converter", SampleProfiler.cleanWrapper(TestSampleProfiling.LIVE_WRAPPER));
        assertThat(((Map<?, ?>) ((Map<?, ?>) result.get("xslt_input")).get("mapping")).get("xml_namespace"))
                .isEqualTo("event-logging:3");
        assertThat((String) result.get("hint")).contains("xml_namespace 'event-logging:3'");
        final TextConverterDoc doc = (TextConverterDoc) world.world.docs.get(String.valueOf(
                ((Map<?, ?>) result.get("saved")).get("uuid")));
        assertThat(doc.getConverterType()).isEqualTo(TextConverterType.XML_FRAGMENT);
    }

    private static Map<String, String> orderedOf(final String k1, final String v1, final String k2, final String v2) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
