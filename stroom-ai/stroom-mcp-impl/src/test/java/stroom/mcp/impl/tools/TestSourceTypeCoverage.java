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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// review_coverage on an aggregated feed: the kinds processing missed judged against the build's scope, and the
/// source types the registry lacks added to the build's registry as new.
class TestSourceTypeCoverage {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "p", "PEGA-Events");
    private static final String RAW = """
            {"sourcetype": "PegaSecurityEvent", "message": "login"}
            {"sourcetype": "PegaDB", "message": "query"}
            {"sourcetype": "PegaBatch", "message": "job"}
            """;

    private static final CoverageReads.Dispatched NOTHING_DISPATCHED = new CoverageReads.Dispatched();

    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final TranslationBuilds builds = Mockito.mock(TranslationBuilds.class);
    private final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final SampleStreamTexts sampleStreamTexts = Mockito.mock(SampleStreamTexts.class);
    private final SourceTypeCoverage coverage = new SourceTypeCoverage(guard, builds, registries, streamReader,
            sampleStreamTexts);
    private final ToolSession session = new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));

    @Test
    void kindsOutsideTheScopeArePassedOverAndNewSourceTypesAddedToTheBuildsRegistry() {
        inBuild("b");
        Mockito.when(builds.surveyStatesInBuild("b")).thenReturn(List.of(survey("b")));
        final SourceTypeRegistries.Found registry = registry(false);
        Mockito.when(registries.registry("b", "PEGA")).thenReturn(Optional.of(registry));
        Mockito.when(registries.writeRegistry(any(), eq("b"), eq("PEGA"), eq(registry), anyString()))
                .thenReturn(new SurveyRecord("r", "PEGA source types", ""));
        raw();
        final List<Map<String, Object>> groups = List.of(group(0, 5), group(1, 40), group(2, 3));

        final SourceTypeCoverage.Judged judged = coverage.judge(session, PIPELINE, groups, NOTHING_DISPATCHED)
                .orElseThrow();

        assertThat(judged.missed()).extracting(group -> group.get("source_type"))
                .containsExactly("PegaSecurityEvent");
        // A new source type is outside the scope too: passed over, and added to the registry for the user to place.
        assertThat(judged.passedOver()).extracting(group -> group.get("source_type"))
                .containsExactly("PegaDB", "PegaBatch (new)");
        assertThat(judged.said()).containsEntry("passed_over", Map.of("PegaDB", 40, "PegaBatch (new)", 3))
                .containsEntry("new_source_types", List.of("PegaBatch"));
        assertThat(JsonValues.mapOrEmpty(judged.said().get("scope"))).containsEntry("share_percent", 25.0);
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(registries).writeRegistry(eq(session), eq("b"), eq("PEGA"), eq(registry),
                written.capture());
        final List<Map<String, Object>> rows = JsonValues.maps(SourceTypeRegistry.read(written.getValue())
                .get("rows"));
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("PegaSecurityEvent", "PegaDB",
                "PegaBatch");
        assertThat(rows.getLast()).containsEntry("status", SourceTypeRegistry.NEW).containsEntry("value",
                "PegaBatch");
        assertThat(JsonValues.mapOrEmpty(rows.getLast().get("decided"))).containsEntry("by", "review_coverage");
    }

    @Test
    void theDispatchersWarningsAddNewSourceTypesAndItsCountsAreReportedBySourceType() {
        inBuild("b");
        Mockito.when(builds.surveyStatesInBuild("b")).thenReturn(List.of());
        // No kind missed: the feed is told from the raw streams the dispatcher logged for.
        Mockito.when(streamReader.meta(12L)).thenReturn(Meta.builder().id(12L).feedName("PEGA").build());
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "PEGA");
        state.put("field", "sourcetype");
        final Map<String, Object> integration = row("PegaIntegration");
        integration.put("status", SourceTypeRegistry.TRANSLATED);
        integration.put("pipeline", Map.of("own", true, "name", "PEGA-Integration-Events"));
        final Map<String, Object> mine = row("PegaSecurityEvent");
        mine.put("status", SourceTypeRegistry.TRANSLATED);
        mine.put("pipeline", Map.of("own", false, "name", PIPELINE.getName()));
        // A value an earlier call added already: not added again.
        final Map<String, Object> earlier = row("PegaQueue");
        earlier.put("status", SourceTypeRegistry.NEW);
        state.put("rows", List.of(mine, row("PegaDB"), integration, earlier));
        final SourceTypeRegistries.Found registry = new SourceTypeRegistries.Found(new SurveyRecord("r",
                "PEGA source types", SourceTypeRegistry.render(state)), false, state);
        Mockito.when(registries.registry("b", "PEGA")).thenReturn(Optional.of(registry));
        Mockito.when(registries.writeRegistry(any(), eq("b"), eq("PEGA"), eq(registry), anyString()))
                .thenReturn(new SurveyRecord("r", "PEGA source types", ""));
        final CoverageReads.Dispatched dispatched = new CoverageReads.Dispatched();
        dispatched.add(12L, "Passed over 40 record(s) of source type PegaDB");
        dispatched.add(12L, "Passed over 3 record(s) of source type (none: no registry row matches)");
        dispatched.add(12L, "Passed over 9 record(s) of source type PegaIntegration, handled by pipeline "
                            + "'PEGA-Integration-Events'");
        dispatched.add(12L, "New source type: no registry row matches sourcetype 'PegaBatch': its records are "
                            + "passed over (review_coverage adds it to the registry as new)");
        dispatched.add(12L, "New source type: no registry row matches sourcetype 'PegaQueue': its records are "
                            + "passed over (review_coverage adds it to the registry as new)");

        final SourceTypeCoverage.Judged judged = coverage.judge(session, PIPELINE, List.of(), dispatched)
                .orElseThrow();

        final Map<String, Object> processing = JsonValues.mapOrEmpty(judged.said().get("processing"));
        assertThat(JsonValues.mapOrEmpty(processing.get("passed_over"))).containsOnlyKeys("PegaDB",
                "(none: no registry row matches)");
        assertThat(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(processing.get("passed_over")).get("PegaDB")))
                .containsEntry("records", 40L).containsEntry("status", SourceTypeRegistry.PARKED);
        // Another pipeline's source type: handled elsewhere, never parked or new.
        assertThat(JsonValues.mapOrEmpty(processing.get("handled_elsewhere"))).containsOnlyKeys("PegaIntegration");
        assertThat(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(processing.get("handled_elsewhere"))
                .get("PegaIntegration"))).containsEntry("pipeline", "pipeline 'PEGA-Integration-Events'")
                .containsEntry("records", 9L);
        assertThat(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(processing.get("new_source_types")).get("PegaQueue")))
                .containsEntry("now_in_registry_as", "PegaQueue");
        assertThat(judged.said()).containsEntry("new_source_types", List.of("PegaBatch"));
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(registries).writeRegistry(eq(session), eq("b"), eq("PEGA"), eq(registry),
                written.capture());
        assertThat(JsonValues.maps(SourceTypeRegistry.read(written.getValue()).get("rows")))
                .extracting(row -> row.get("name"))
                .containsExactly("PegaSecurityEvent", "PegaDB", "PegaIntegration", "PegaQueue", "PegaBatch");
    }

    @Test
    void aKindOfAnotherPipelinesSourceTypeIsHandledElsewhere() {
        Mockito.when(guard.buildOf(PIPELINE)).thenReturn(Optional.empty());
        Mockito.when(streamReader.meta(9L)).thenReturn(Meta.builder().id(9L).feedName("PEGA").build());
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "PEGA");
        state.put("field", "sourcetype");
        final Map<String, Object> db = row("PegaDB");
        db.put("status", SourceTypeRegistry.TRANSLATED);
        db.put("pipeline", Map.of("own", true, "name", "PEGA-DB-Events"));
        state.put("rows", List.of(row("PegaSecurityEvent"), db));
        Mockito.when(registries.registry(null, "PEGA")).thenReturn(Optional.of(new SourceTypeRegistries.Found(
                new SurveyRecord("r", "PEGA source types", SourceTypeRegistry.render(state)), true, state)));
        raw();

        final SourceTypeCoverage.Judged judged = coverage.judge(session, PIPELINE, List.of(group(0, 5),
                group(1, 40)), NOTHING_DISPATCHED).orElseThrow();

        assertThat(judged.missed()).extracting(group -> group.get("source_type")).containsExactly(
                "PegaSecurityEvent");
        assertThat(judged.handled()).singleElement().satisfies(group -> assertThat(group)
                .containsEntry("handled_by", "pipeline 'PEGA-DB-Events'"));
    }

    @Test
    void outsideABuildNothingIsWrittenAndNothingPassedOver() {
        Mockito.when(guard.buildOf(PIPELINE)).thenReturn(Optional.empty());
        Mockito.when(streamReader.meta(9L)).thenReturn(Meta.builder().id(9L).feedName("PEGA").build());
        Mockito.when(registries.registry(null, "PEGA")).thenReturn(Optional.of(registry(true)));
        raw();

        final SourceTypeCoverage.Judged judged = coverage.judge(session, PIPELINE, List.of(group(0, 5),
                group(2, 3)), NOTHING_DISPATCHED).orElseThrow();

        assertThat(judged.passedOver()).isEmpty();
        assertThat(judged.scope()).isNull();
        assertThat(String.valueOf(judged.said().get("registry"))).startsWith("not written: only a build");
        Mockito.verify(registries, Mockito.never()).writeRegistry(any(), any(), any(), any(), any());
    }

    @Test
    void aFeedWithNoSourceTypeFieldIsNotJudged() {
        Mockito.when(guard.buildOf(PIPELINE)).thenReturn(Optional.empty());
        Mockito.when(streamReader.meta(9L)).thenReturn(Meta.builder().id(9L).feedName("PLAIN").build());
        Mockito.when(registries.registry(null, "PLAIN")).thenReturn(Optional.empty());

        assertThat(coverage.judge(session, PIPELINE, List.of(group(0, 5)), NOTHING_DISPATCHED)).isEmpty();
        assertThat(coverage.judge(session, PIPELINE, List.of(), NOTHING_DISPATCHED)).isEmpty();
    }

    private void inBuild(final String build) {
        Mockito.when(guard.buildOf(PIPELINE)).thenReturn(Optional.of(build));
    }

    private void raw() {
        Mockito.when(sampleStreamTexts.read(List.of(9L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 9", RAW), List.of()));
    }

    // A kind found, its first example the raw record given.
    private static Map<String, Object> group(final int record, final int records) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("records", records);
        group.put("examples", new ArrayList<>(List.of(Map.of("raw_stream", 9L, "record", record))));
        return group;
    }

    private static Map<String, Object> survey(final String build) {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("PegaSecurityEvent", Map.of("records", 1));
        values.put("PegaDB", Map.of("records", 3));
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "PEGA");
        state.put("source_types", Map.of("field", "sourcetype", "records", 4, "values", values, "scope", Map.of(
                SourceTypeScope.BUILD, build, SourceTypeScope.SOURCE_TYPES, List.of("PegaSecurityEvent"))));
        return state;
    }

    private static SourceTypeRegistries.Found registry(final boolean promoted) {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "PEGA");
        state.put("field", "sourcetype");
        state.put("rows", List.of(row("PegaSecurityEvent"), row("PegaDB")));
        return new SourceTypeRegistries.Found(new SurveyRecord("r", "PEGA source types",
                SourceTypeRegistry.render(state)), promoted, state);
    }

    private static Map<String, Object> row(final String name) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", "value");
        row.put("value", name);
        row.put("status", SourceTypeRegistry.PARKED);
        return row;
    }
}
