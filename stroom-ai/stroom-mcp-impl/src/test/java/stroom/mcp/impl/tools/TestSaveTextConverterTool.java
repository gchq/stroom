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
import stroom.ai.impl.tools.draft.SplitterXml;
import stroom.ai.impl.tools.sample.DataSplitterSchema;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// save_text_converter, and the converter checks.
class TestSaveTextConverterTool {

    // One field: the whole line.
    private static final String SPLITTER = DataSplitterSchema.ROOT
                                           + "<regex pattern=\"^(.*)$\"><data name=\"line\" value=\"$1\"/></regex>"
                                           + "</dataSplitter>";
    // As seen in testing, written by hand for a space-delimited Citrix feed: the fields named with <entry>, which
    // Stroom's parser ignores, and group on the regex, so its records had no fields.
    private static final String CITRIX = DataSplitterSchema.ROOT + "\n"
                                         + "  <regex pattern=\"^(\\S+) (\\S+) (.*)$\" group=\"0\">\n"
                                         + "    <entry>ReceiveTime</entry>\n"
                                         + "    <entry>EventTime</entry>\n"
                                         + "    <entry>Body</entry>\n"
                                         + "  </regex>\n"
                                         + "</dataSplitter>";

    private final TranslationWorld world = new TranslationWorld();
    private final SaveTextConverterTool tool = new SaveTextConverterTool(world.docs, world.plan,
            world.sampleStreamTexts, world.dataSplitterSchema);

    @Test
    void aDataSplitterMayNotAddIgnoreErrors() {
        // It hides the errors for text no expression matches, which is then lost without a word.
        final String ignoring = SPLITTER.replace("<dataSplitter ", "<dataSplitter ignoreErrors=\"true\" ");
        assertThat(ignoring).contains("ignoreErrors");
        assertThatThrownBy(() -> TranslationDocs.checkConverter("DATA_SPLITTER", ignoring))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("ignoreErrors hides the errors")
                .hasMessageContaining("catch_all");
        // One the converter it replaces already had is kept.
        TranslationDocs.checkConverter("DATA_SPLITTER", ignoring, ignoring);
    }

    @Test
    void aTextConverterIsADataSplitterNeverAJsonParser() {
        assertThatThrownBy(() -> TranslationDocs.checkConverter("DATA_SPLITTER",
                "<textConverter><jsonParser/></textConverter>")).hasMessageContaining("cannot parse JSON");
        assertThatThrownBy(() -> TranslationDocs.checkConverter("DATA_SPLITTER", "<records xmlns=\"records:2\"/>"))
                .hasMessageContaining("root element is <dataSplitter");
        TranslationDocs.checkConverter("DATA_SPLITTER", SPLITTER);
        TranslationDocs.checkConverter("XML_FRAGMENT", "<!DOCTYPE records [<!ENTITY fragment SYSTEM \"fragment\">]>"
                                                       + "<records>&fragment;</records>");
    }

    @Test
    void theWrapperIsChecked() {
        TranslationDocs.checkConverter("XML_FRAGMENT", SampleProfiler.EVENTS_FRAGMENT_WRAPPER.replace("{version}",
                "4.1.0"));
        assertThatThrownBy(() -> TranslationDocs.checkConverter("XML_FRAGMENT", "<records xmlns=\"records:2\"/>"))
                .hasMessageContaining("&fragment;");
    }

    @Test
    void aConverterIsCreatedInTheBuildThenChangedByUuid() {
        final Map<String, Object> made = world.call(tool, Map.of("build", "acme-v1", "name", "ACME-SPLITTER",
                "converter_type", "DATA_SPLITTER", "code", SPLITTER));
        assertThat(made).containsEntry("type", TextConverterDoc.TYPE).containsEntry("name", "ACME-SPLITTER");
        final String uuid = String.valueOf(made.get("uuid"));
        final TextConverterDoc doc = (TextConverterDoc) world.world.docs.get(uuid);
        assertThat(doc.getConverterType()).isEqualTo(TextConverterType.DATA_SPLITTER);
        assertThat(doc.getData()).isEqualTo(SPLITTER);
        // In the build's Events folder until a pipeline writing Records runs it.
        assertThat(world.world.guard().folderContents("acme-v1").getFirst().role()).isEqualTo("Events");

        final String changed = SPLITTER.replace("name=\"line\"", "name=\"text\"");
        assertThat(world.call(tool, Map.of("build", "acme-v1", "name", "ACME-SPLITTER", "converter_type",
                "DATA_SPLITTER", "code", changed, "uuid", uuid))).containsEntry("uuid", uuid);
        assertThat(((TextConverterDoc) world.world.docs.get(uuid)).getData()).isEqualTo(changed);
        // The converter's own type is checked: JSON is refused.
        assertThat(String.valueOf(world.call(tool, Map.of("build", "acme-v1", "name", "ACME-SPLITTER",
                "converter_type", "DATA_SPLITTER", "code", "<jsonParser/>", "uuid", uuid)).get("error")))
                .contains("cannot parse JSON");
    }

    @Test
    void onlyAConverterTheToolsCreatedIsChanged() {
        final String uuid = world.world.add(TextConverterDoc.TYPE, "Theirs", world.world.folder("System/Feeds").uuid,
                null).uuid;

        assertThat(String.valueOf(world.call(tool, Map.of("build", "acme-v1", "name", "Theirs", "converter_type",
                "DATA_SPLITTER", "code", SPLITTER, "uuid", uuid)).get("error")))
                .contains("was not created by this server");
    }

    @Test
    void anUpdateByUuidNeedsNoBuildOrName() {
        // Found reviewing the tools: the schema required build and name, which an update by uuid doesn't give, so a
        // client checking calls against the schema refused a valid one.
        final List<String> required = tool.definition().parameters().required();

        assertThat(required).containsExactly("code");
    }

    @Test
    void aConverterStroomsSchemaRefusesIsNotSaved() {
        final String error = error(CITRIX);

        assertThat(error).startsWith("Not saved: ").contains("schema error(s)")
                .contains("line 2 ").contains("group")
                .contains("line 3 ").contains("entry")
                // What to write instead, and the tool that writes it.
                .contains("<data name=\"field\" value=\"$1\"/>").contains("build_data_splitter");
        assertThat(world.world.docs.values()).noneMatch(TextConverterDoc.class::isInstance);
    }

    @Test
    void aConverterWithNoDataElementIsNotSaved() {
        // Valid against the schema, but its records would have no fields, with no error.
        final String noData = DataSplitterSchema.ROOT + "<regex pattern=\"^(.*)$\"/></dataSplitter>";

        assertThat(error(noData)).contains("has no <data> element").contains("build_data_splitter");
    }

    @Test
    void aConverterWithNoSchemaLocationIsNotSaved() {
        final String bare = "<dataSplitter xmlns=\"data-splitter:3\" version=\"3.0\"><regex pattern=\"^(.*)$\">"
                            + "<data name=\"line\" value=\"$1\"/></regex></dataSplitter>";

        assertThat(error(bare)).contains("no xsi:schemaLocation").contains(DataSplitterSchema.ROOT);
    }

    @Test
    void aPatternThatDoesntCompileIsNotSaved() {
        final String bad = SPLITTER.replace("^(.*)$", "(unclosed");

        assertThat(error(bad)).contains("Stroom can't read the converter").contains("(unclosed");
    }

    @Test
    void withNoDataSplitterSchemaHeldNoneIsSaved() {
        // Stroom's DSParser fails every stream without it.
        world.dataSplitterXsd = null;

        assertThat(error(SPLITTER)).isEqualTo("Not saved: " + DataSplitterSchema.NOT_HELD);
    }

    @Test
    void theConvertersBuildDataSplitterWritesAreSaved() {
        int n = 0;
        for (final Map<String, Object> example : SplitterXml.EXAMPLES.values()) {
            final Map<String, Object> saved = world.call(tool, Map.of("build", "acme-v1", "name", "SPLITTER-" + n++,
                    "converter_type", "DATA_SPLITTER", "code", SplitterXml.converter(SplitterSpec.fromMap(example))));

            assertThat(saved).as(String.valueOf(example)).doesNotContainKey("error").containsKey("uuid");
        }
    }

    @Test
    void withNoSampleStreamTheResultSaysItWasntRun() {
        final Map<String, Object> saved = world.call(tool, Map.of("build", "acme-v1", "name", "ACME-SPLITTER",
                "converter_type", "DATA_SPLITTER", "code", SPLITTER));

        assertThat(String.valueOf(saved.get("sample_run"))).startsWith("Not run on a sample");
    }

    @Test
    void itIsRunOnTheBuildsSampleStreamsBeforeItIsSaved() {
        world.facts.ownRaw = List.of(7L);
        Mockito.when(world.sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", "2026-11-02 host1 user logged on\n2026-11-02 host2 user logged off\n"),
                List.of()));
        final String spaced = DataSplitterSchema.ROOT
                              + "<regex pattern=\"^(\\S+) (\\S+) (.*)\\n\">"
                              + "<data name=\"time\" value=\"$1\"/><data name=\"host\" value=\"$2\"/>"
                              + "<data name=\"message\" value=\"$3\"/></regex></dataSplitter>";

        final Map<String, Object> saved = world.call(tool, Map.of("build", "acme-v1", "name", "ACME-SPLITTER",
                "converter_type", "DATA_SPLITTER", "code", spaced));

        assertThat(saved).containsKey("uuid");
        assertThat(String.valueOf(saved.get("sample_run"))).contains("wrote 2 record(s)").contains("[7]");
    }

    @Test
    void aConverterStroomsParserReportsErrorsForOnTheSampleIsNotSaved() {
        world.facts.ownRaw = List.of(7L);
        Mockito.when(world.sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", "2026-11-02 host1 user logged on\nnot a line it matches\n"), List.of()));
        // Two fields then the rest, each line to its new line: the second line has no two fields.
        final String strict = DataSplitterSchema.ROOT
                              + "<regex pattern=\"^(\\d{4}-\\d{2}-\\d{2}) (\\S+) (.*)\\n\">"
                              + "<data name=\"time\" value=\"$1\"/><data name=\"host\" value=\"$2\"/>"
                              + "<data name=\"message\" value=\"$3\"/></regex></dataSplitter>";

        assertThat(error(strict)).startsWith("Not saved: Stroom's parser reports").contains("stream 7")
                .contains("build_data_splitter");
        assertThat(world.world.docs.values()).noneMatch(TextConverterDoc.class::isInstance);
    }

    private String error(final String code) {
        final Map<String, Object> result = world.call(tool, Map.of("build", "acme-v1", "name", "ACME-SPLITTER",
                "converter_type", "DATA_SPLITTER", "code", code));
        assertThat(result).containsKey("error");
        return String.valueOf(result.get("error"));
    }
}
