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

import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// build_reference_xslt, the tool (the XSLT itself is [stroom.ai.impl.tools.generation.ReferenceGenerator]'s, tested
/// with it).
class TestBuildReferenceXsltTool {

    private static final Map<String, Object> MAPPING = Map.of("input", "json", "maps", List.of(Map.of(
            "name", "HOSTS", "key", "host", "values", List.of(Map.of("field", "ip")))));

    private final TranslationWorld world = new TranslationWorld();
    private final BuildReferenceXsltTool tool = new BuildReferenceXsltTool(world.heldSchemas, world.requiredImports);

    @Test
    void aReferenceXsltBuiltForABuildGetsTheRequiredImports() {
        world.world.folder("System/" + BuildWorld.WORKSPACE + "/b1");
        Mockito.when(world.agentInstructions.importsRequired(any(), eq(InstructionRules.REFERENCE), any(), any()))
                .thenReturn(List.of(new RequiredImport("Ref-Common-V1", "/")));

        final Map<String, Object> built = world.call(tool, Map.of("mapping", MAPPING, "schema_version", "2.0.1",
                "build", "b1"));

        assertThat((String) built.get("xslt")).contains("<xsl:import href=\"Ref-Common-V1\"/>");
        assertThat((List<?>) built.get("required_imports")).singleElement().satisfies(note ->
                assertThat(ToolJson.dumps(note)).contains("\"import\": \"Ref-Common-V1\"")
                        .contains("\"required_by\": \"the AGENTS doc in (root)\""));
        // Asked for the reference stage of the build's folder.
        Mockito.verify(world.agentInstructions).importsRequired(any(), eq(InstructionRules.REFERENCE),
                eq(List.of("System/" + BuildWorld.WORKSPACE + "/b1")), eq(List.of()));
    }

    @Test
    void theNewestReferenceDataSchemaStroomHoldsIsWrittenWhereNoneIsGiven() {
        Mockito.when(world.heldSchemas.systemIds()).thenReturn(List.of("file://event-logging-v4.1.0.xsd",
                "file://reference-data-v2.0.1.xsd", "file://reference-data-v2.0.2.xsd"));

        final Map<String, Object> built = world.call(tool, Map.of("mapping", MAPPING));

        assertThat(built).containsEntry("ok", true).containsEntry("schema_version", "2.0.2");
        assertThat((String) built.get("xslt")).contains("file://reference-data-v2.0.2.xsd");
        assertThat((String) built.get("hint")).startsWith("save_xslt it");
        // Holding none, 2.0.1.
        Mockito.when(world.heldSchemas.systemIds()).thenReturn(List.of());
        assertThat(world.call(tool, Map.of("mapping", MAPPING))).containsEntry("schema_version", "2.0.1");
    }

    @Test
    void theNewestIsTheHighestVersionWhateverOrderStroomListsThemIn() {
        // Found reviewing the tools: the last listed was taken as the newest. 2.0.10 is newer than 2.0.9, though
        // it sorts before it as text.
        Mockito.when(world.heldSchemas.systemIds()).thenReturn(List.of("file://reference-data-v2.0.10.xsd",
                "file://reference-data-v2.0.9.xsd", "file://reference-data-v2.0.2.xsd"));

        assertThat(world.call(tool, Map.of("mapping", MAPPING))).containsEntry("schema_version", "2.0.10");
    }

    @Test
    void aMappingThatIsNotOneIsRefused() {
        assertThat((String) world.call(tool, Map.of("mapping", Map.of("input", "json"))).get("error"))
                .startsWith("mapping is not a reference mapping: ");
    }
}
