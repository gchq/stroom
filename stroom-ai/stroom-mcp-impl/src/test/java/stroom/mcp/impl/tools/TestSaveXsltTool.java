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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// save_xslt (the Stroom MCP server's `tools/translation.py` `save_xslt`: its cases in `tests/test_generation.py`,
/// `tests/test_surface.py`, `tests/test_index_names.py`, `tests/test_required_imports.py` and `tests/test_saxon.py`).
class TestSaveXsltTool {

    private static final String BUILD = "acme-v1";
    private static final String HAND = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xpath-default-namespace="records:2" \
            version="3.0">
              <xsl:template match="record">
                <Event><Description>Printed document (A4)</Description>{BODY}</Event>
              </xsl:template>
            </xsl:stylesheet>""";

    private final TranslationWorld world = new TranslationWorld();

    @Test
    void saveXsltGeneratesAnIndexingXsltFromItsPlanAndTakesNoMapping() {
        // A translation from a mapping is saved by build_translation_xslt; save_xslt's copy of the mapping schema
        // was ~4,500 tokens of tool definition in every model's context.
        final SaveXsltTool tool = world.saveXsltTool();
        assertThat(tool.definition().parameters().properties()).doesNotContainKey("mapping");
        final Map<String, Object> plan = lucenePlan();

        final Map<String, Object> saved = world.call(tool, Map.of("build", BUILD, "name", "ACME-INDEX-XSLT",
                "index_plan", plan));

        final XsltDoc xslt = world.xslt(String.valueOf(saved.get("uuid")));
        assertThat(xslt.getData()).isEqualTo(FieldPlan.fromJson(plan).xslt());
        assertThat(FieldPlan.fromJson(KeptMapping.readMapping(xslt.getDescription()).orElseThrow().payload()))
                .isEqualTo(FieldPlan.fromJson(plan));
        assertThat(String.valueOf(world.call(tool, Map.of("build", BUILD, "name", "ACME-Events")).get("error")))
                .contains("build_translation_xslt");
    }

    @Test
    void saveToolsCreateOrUpdateByUuid() {
        final SaveXsltTool tool = world.saveXsltTool();
        final String code = HAND.replace("{BODY}", "");
        final Map<String, Object> made = world.call(tool, Map.of("build", BUILD, "name", "n", "code", code));
        assertThat(made).containsEntry("name", "n").containsKey("uuid");
        final String uuid = String.valueOf(made.get("uuid"));
        final String version = String.valueOf(made.get("version"));
        assertThat(world.call(tool, Map.of("build", BUILD, "name", "n", "code", code, "uuid", uuid, "version",
                version))).containsEntry("uuid", uuid);
        // A version from before someone else's save: refused, saying so.
        assertThat(String.valueOf(world.call(tool, Map.of("build", BUILD, "name", "n", "code", code, "uuid", uuid,
                "version", version)).get("error"))).contains("changed since it was read");
        assertThat(world.xslt(uuid).getDescription()).contains("Created by hand").contains("Saved again by hand");
    }

    @Test
    void theSessionsAmendedPlanIsSavedWithNoCodeAndNoPlan() {
        // Seen in Ask Stroom AI: after the client's step cap the agent had lost its earlier extra_fields, and the
        // 50-field plan went inline to save_xslt, create_index_doc and more. Saved with no code and no plan: the one
        // amended in the session, not pasted again.
        final FieldPlan drafted = FieldPlan.fromJson(lucenePlan());
        final SaveXsltTool tool = world.saveXsltTool(session -> Map.of("acme", drafted));

        final Map<String, Object> saved = world.call(tool, Map.of("build", "fw-v1", "name", "FW-INDEX-XSLT"));

        assertThat(world.xslt(String.valueOf(saved.get("uuid"))).getData()).isEqualTo(drafted.xslt());
        // With several drafted, and no XSLT to tell which, none is taken.
        final SaveXsltTool several = world.saveXsltTool(session -> Map.of("acme", drafted, "other", drafted));
        assertThat(String.valueOf(world.call(several, Map.of("build", "fw-v1", "name", "FW-2")).get("error")))
                .contains("draft_index_mapping drafted several, or none");
    }

    @Test
    void anIndexingXsltSavedFromItsPlanGetsTheRequiredImports() {
        Mockito.when(world.agentInstructions.importsRequired(any(), eq(InstructionRules.INDEXING), any(), any()))
                .thenReturn(List.of(new RequiredImport("Common-Index-V1", "System")));
        // The import is found, as Stroom imports by name.
        world.inBuild("shared", XsltDoc.TYPE, "Common-Index-V1");
        Mockito.when(world.docFinder.findByName(eq(XsltDoc.TYPE), eq("Common-Index-V1"), Mockito.anyBoolean()))
                .thenReturn(List.of(world.world.nodes.values().stream()
                        .filter(node -> "Common-Index-V1".equals(node.name))
                        .findFirst()
                        .orElseThrow()
                        .docRef()));

        final Map<String, Object> saved = world.call(world.saveXsltTool(), Map.of("build", BUILD, "name",
                "ACME - Indexing", "index_plan", elasticPlan()));

        final XsltDoc xslt = world.xslt(String.valueOf(saved.get("uuid")));
        assertThat(xslt.getData()).contains("<xsl:import href=\"Common-Index-V1\"");
        assertThat(KeptMapping.readMapping(xslt.getDescription()).orElseThrow().payload().get("imports"))
                .isEqualTo(List.of("Common-Index-V1"));
    }

    @Test
    void savingOverAnXsltIsHeldToTheRequiredImportsToo() {
        Mockito.when(world.agentInstructions.importsRequired(any(), eq(InstructionRules.TRANSLATION), any(), any()))
                .thenReturn(List.of(new RequiredImport("Stream-Meta-V1", "System")));
        final String uuid = world.inBuild(BUILD, XsltDoc.TYPE, "ACME-Events").getUuid();
        final String hand = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                            + "<xsl:template match=\"/\"><Events xmlns=\"event-logging:3\"/></xsl:template>"
                            + "</xsl:stylesheet>";

        final Map<String, Object> refused = world.call(world.saveXsltTool(), Map.of("build", BUILD, "name",
                "ACME-Events", "code", hand, "uuid", uuid));

        assertThat(String.valueOf(refused.get("error"))).contains("import 'Stream-Meta-V1'");
    }

    @Test
    void checkXsltRefusesItAndASaveKeepsWhatTheProductionXsltAlreadyDid() {
        final String unsafe = HAND.replace("{BODY}", "<xsl:value-of select=\"document('lookup.xml')/x\"/>");
        assertThat(refusal(() -> world.docs.checked(world.context(), unsafe, null, null)).getMessage())
                .contains("document()");
        // A working copy of a production XSLT that already calls document(): saved, but nothing new is let in.
        world.docs.checked(world.context(), unsafe, null, unsafe);
        assertThat(refusal(() -> world.docs.checked(world.context(), unsafe.replace("</Event>",
                "<xsl:value-of select=\"environment-variable('X')\"/></Event>"), null, unsafe)).getMessage())
                .contains("environment-variable");
    }

    @Test
    void anXsltIsFiledByWhatItWrites() {
        assertThat(XsltSaves.xsltRole("<Events xmlns=\"event-logging:3\"/>")).isEqualTo("Events");
        assertThat(XsltSaves.xsltRole("<xsl:stylesheet xpath-default-namespace=\"event-logging:3\"><records "
                                      + "xmlns=\"records:2\"/>")).isEqualTo("Indexing");
        assertThat(XsltSaves.xsltRole("<xsl:stylesheet xpath-default-namespace=\"records:2\"><records "
                                      + "xmlns=\"records:2\"/>")).isEqualTo("Records");
        assertThat(XsltSaves.xsltRole("<array xmlns=\"http://www.w3.org/2013/XSL/json\"/>")).isEqualTo("Indexing");
        // And made in that role's folder of the build.
        final Map<String, Object> made = world.call(world.saveXsltTool(), Map.of("build", BUILD, "name", "R",
                "code", HAND.replace("{BODY}", "")));
        assertThat(world.world.guard().folderContents(BUILD).stream()
                .filter(doc -> doc.uuid().equals(made.get("uuid")))
                .findFirst()
                .orElseThrow()
                .role()).isNull();
    }

    private static ToolRefusal refusal(final Runnable run) {
        try {
            run.run();
        } catch (final ToolRefusal e) {
            return e;
        }
        throw new AssertionError("Not refused");
    }

    private static Map<String, Object> lucenePlan() {
        final Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("backend", "lucene");
        plan.put("index_name", "acme");
        plan.put("time_field", "EventTime");
        plan.put("fields", List.of(Map.of("name", "StreamId", "type", "id", "source", "@StreamId"),
                Map.of("name", "EventTime", "type", "date", "source", "EventTime/TimeCreated")));
        return plan;
    }

    private static Map<String, Object> elasticPlan() {
        final Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("backend", "elasticsearch");
        plan.put("index_name", "ecs-acme-v1");
        plan.put("time_field", "@timestamp");
        plan.put("fields", List.of(Map.of("name", "@timestamp", "type", "date", "source", "EventTime/TimeCreated")));
        return plan;
    }
}
