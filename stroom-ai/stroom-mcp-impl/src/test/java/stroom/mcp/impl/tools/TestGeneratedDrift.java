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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Whether an XSLT is still what its kept mapping or plan generates ([GeneratedDrift]: the Stroom MCP server's
/// `tools/builds.py` `_drift`, `_plan_drift` and `lost_mappings`), as `tests/test_version_history.py` and
/// `tests/test_hand_edits.py` test them.
class TestGeneratedDrift {

    private static final String METHOD = "<string key=\"method\"><xsl:value-of "
                                         + "select=\"EventDetail/*/Resource/HTTPMethod\"/></string>";

    private static final Instant DAY = Instant.parse("2026-10-10T09:00:00Z");

    private final TranslationWorld world = new TranslationWorld();
    private final GeneratedDrift drift = new GeneratedDrift(world.keptCode, world.history,
            world.world.pipelineShapes, world.world.documentAccess, world.docs);

    @Test
    void driftTellsAGeneratorUpgradeFromAnEditMadeByHand() {
        // Seen in production after 0.16.43: build_status called a generated XSLT "edited by hand", because the
        // generator's defaults had changed, and the agent went looking for the edit.
        final TranslationMapping mapping = TranslationWorld.parsed();
        final Map<String, Object> styled = TranslationWorld.mapping();
        styled.put("style", Map.of("variables", "top", "data_values", "attribute"));
        final String old = generate(TranslationMapping.parse(styled));
        final String saved = XsltVersionHistory.withPending("", null, "pk", "Created", "stroom-mcp 0.16.42", DAY,
                old);

        final String upgraded = translationDrift(mapping, old, saved).orElseThrow();
        assertThat(upgraded).contains("written by an earlier generator (stroom-mcp 0.16.42) and is unchanged since")
                .contains("build_translation_xslt uuid='x-1' (no mapping) regenerates it")
                .doesNotContain("by hand");
        assertThat(translationDrift(mapping, old.replace("<EventSource>", "<EventSource><!-- mine -->"), saved)
                .orElseThrow()).startsWith("its XSLT was edited by hand since the server saved it");
        assertThat(translationDrift(mapping, old, "").orElseThrow())
                .contains("edited by hand, or written by an earlier version of the generator");
        assertThat(translationDrift(mapping, generate(mapping), saved)).isEmpty();
    }

    @Test
    void aHandEditIsShownSoItCanBeCarriedIntoTheMapping() {
        // Asked for by the user: an XSLT changed by hand to write a new field, or to stop writing one. build_status
        // says which lines differ from what the mapping generates, so the agent can carry them into the mapping.
        final TranslationMapping mapping = TranslationWorld.parsed();
        final String code = generate(mapping);
        final String savedBy = XsltVersionHistory.withPending("", null, "pk", "Created", world.history.madeBy(),
                DAY, code);
        final String added = code.replaceFirst("</Authenticate>",
                "<Data Name=\"device_class\" Value=\"{data[@name='class']/@value}\"/></Authenticate>");
        final String removed = code.replaceFirst("<Generator>vpnd</Generator>", "");
        assertThat(added).isNotEqualTo(code);
        assertThat(removed).isNotEqualTo(code);
        for (final Map.Entry<String, String> edit : Map.of(added, "+ <Data Name=\"device_class\"", removed,
                "- <Generator>vpnd</Generator>").entrySet()) {
            final String said = translationDrift(mapping, edit.getKey(), savedBy).orElseThrow();
            assertThat(said).startsWith("its XSLT was edited by hand since the server saved it").contains("changes=");
            final String what = said.split("What differs \\(- what the mapping generates, \\+ the XSLT\\): ")[1];
            // That line, and only that.
            assertThat(what).startsWith(edit.getValue()).doesNotContain(" | ");
        }
    }

    @Test
    void aMappingThatNoLongerGeneratesIsSaid() {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", TranslationWorld.VERSION);
        payload.put("mapping", Map.of("input", "data_splitter", "events", List.of(Map.of("name", "bare",
                "fields", List.of(Map.of("path", "EventDetail/Nope", "value", "x"))))));
        final XsltDoc xslt = XsltDoc.builder().uuid("x-1").name("Acme").data("<xsl:stylesheet/>").build();

        assertThat(drift.drift(new KeptMappings.KeptXslt(new KeptMapping.Kept("translation", payload),
                "translationFilter", xslt)).orElseThrow())
                // Its first two problems, as the Python server wrote the list.
                .startsWith("the mapping kept with its XSLT no longer generates: [\"[bare] EventDetail/Nope: ")
                .endsWith(", '[bare] Event/EventTime is required by the schema; map it']");
    }

    @Test
    void buildStatusSaysAnIndexXsltWasEditedByHand() {
        // Before, only a translation's hand edit was reported: an index or CEF plan's said nothing until it was
        // undone.
        final FieldPlan plan = indexPlan();
        final String code = plan.xslt();
        final String description = XsltVersionHistory.withPending(KeptMapping.withMapping("", "index",
                plan.toJson()), null, "pk", "Created", world.history.madeBy(), DAY, code);
        assertThat(planDrift(plan, code, description)).isEmpty();

        final String edited = editByHand(code, METHOD, METHOD + "<map key=\"body\"><number key=\"bytes\">"
                                                       + "<xsl:value-of select=\"EventDetail/View/Resource/"
                                                       + "InboundSize\"/></number></map>");
        final String said = planDrift(plan, edited, description).orElseThrow();
        assertThat(said).startsWith("its XSLT was edited by hand since the server saved it, and differs from what "
                                    + "its index plan")
                .contains("carry it into the index plan")
                .contains("+ <number key=\"bytes\">");
    }

    @Test
    void anXsltSavedWithAMappingThatIsGoneIsSaid() {
        final DocRef ref = world.inBuild("acme-v1", XsltDoc.TYPE, "Acme");
        world.world.docs.put(ref.getUuid(), XsltDoc.builder().uuid(ref.getUuid()).name("Acme")
                .data("<xsl:stylesheet/>").build());
        PipelineLayers.given(world.world.pipelineService, "p", PipelineLayers.of(
                PipelineLayers.layer("t").chain("dsParser", "DSParser", "translationFilter", "XSLTFilter"),
                PipelineLayers.layer("p").property("translationFilter", "xslt", ref)));
        world.inBuild("acme-v1", PipelineDoc.TYPE, "Pipe");
        assertThat(drift.lostMappings("p")).isEmpty();

        world.world.tags(ref.getUuid()).add(WriteGuard.KEPT_MAPPING);
        assertThat(drift.lostMappings("p")).singleElement().satisfies(lost -> assertThat(lost)
                .startsWith("XSLT 'Acme' was saved by stroom-mcp with the mapping (or plan) it is generated from")
                .contains("rebuild_mapping uuid='" + ref.getUuid() + "'"));
    }

    private Optional<String> translationDrift(final TranslationMapping mapping, final String code,
                                              final String description) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", TranslationWorld.VERSION);
        payload.put("mapping", TranslationWorld.kept(mapping));
        final XsltDoc xslt = XsltDoc.builder().uuid("x-1").name("Acme").data(code).description(description).build();
        return drift.drift(new KeptMappings.KeptXslt(new KeptMapping.Kept("translation", payload),
                "translationFilter", xslt));
    }

    private Optional<String> planDrift(final FieldPlan plan, final String code, final String description) {
        final XsltDoc xslt = XsltDoc.builder().uuid("x-1").name("ACME-INDEX-XSLT").data(code)
                .description(description).build();
        return drift.planDrift(new KeptMappings.KeptXslt(new KeptMapping.Kept("index", plan.toJson()),
                "xsltFilter", xslt));
    }

    private static String generate(final TranslationMapping mapping) {
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, TranslationWorld.SCHEMA,
                TranslationWorld.VERSION);
        assertThat(generated.problems()).isEmpty();
        return generated.xslt();
    }

    // old as the user sees it in the editor: matched whatever the whitespace between its elements.
    private static String editByHand(final String code, final String old, final String now) {
        final StringBuilder pattern = new StringBuilder();
        for (final String part : old.split("(?<=>)\\s*(?=<)")) {
            if (!pattern.isEmpty()) {
                pattern.append("\\s*");
            }
            pattern.append(Pattern.quote(part));
        }
        final Matcher matcher = Pattern.compile(pattern.toString()).matcher(code);
        assertThat(matcher.find()).isTrue();
        return code.substring(0, matcher.start()) + now + code.substring(matcher.end());
    }

    private static FieldPlan indexPlan() {
        return FieldPlan.fromJson(Map.of("backend", "elasticsearch", "index_name", "ecs-acme-v1",
                "time_field", "@timestamp", "convention", "ecs", "fields", List.of(
                        field("StreamId", "id", "@StreamId"),
                        field("EventId", "id", "@EventId"),
                        field("@timestamp", "date", "EventTime/TimeCreated"),
                        field("user.name", "keyword", "EventSource/User/Id"),
                        field("url.original", "keyword", "EventDetail/*/Resource/URL"),
                        field("http.request.method", "keyword", "EventDetail/*/Resource/HTTPMethod"))));
    }

    private static Map<String, Object> field(final String name, final String type, final String source) {
        return Map.of("name", name, "type", type, "source", source);
    }
}
