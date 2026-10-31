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
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// save_text_converter, and the converter checks (the Stroom MCP server's `tools/translation.py`
/// `save_text_converter` and `_check_converter`: their cases in `tests/test_authoring_guards.py` and
/// `tests/test_xml_fragments.py`).
class TestSaveTextConverterTool {

    private static final String SPLITTER = "<dataSplitter xmlns=\"data-splitter:3\" version=\"3.0\"/>";

    private final TranslationWorld world = new TranslationWorld();
    private final SaveTextConverterTool tool = new SaveTextConverterTool(world.docs);

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

        final String changed = SPLITTER.replace("/>", "><regex pattern=\"x\"/></dataSplitter>");
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
}
