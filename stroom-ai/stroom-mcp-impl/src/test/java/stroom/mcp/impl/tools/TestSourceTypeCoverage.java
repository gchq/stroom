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

        final SourceTypeCoverage.Judged judged = coverage.judge(session, PIPELINE, groups).orElseThrow();

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
    void outsideABuildNothingIsWrittenAndNothingPassedOver() {
        Mockito.when(guard.buildOf(PIPELINE)).thenReturn(Optional.empty());
        Mockito.when(streamReader.meta(9L)).thenReturn(Meta.builder().id(9L).feedName("PEGA").build());
        Mockito.when(registries.registry(null, "PEGA")).thenReturn(Optional.of(registry(true)));
        raw();

        final SourceTypeCoverage.Judged judged = coverage.judge(session, PIPELINE, List.of(group(0, 5),
                group(2, 3))).orElseThrow();

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

        assertThat(coverage.judge(session, PIPELINE, List.of(group(0, 5)))).isEmpty();
        assertThat(coverage.judge(session, PIPELINE, List.of())).isEmpty();
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
