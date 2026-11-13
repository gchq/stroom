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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/// record_source_types' scope against what the survey read: a scope that adds source types some stream wasn't read
/// for starts the survey's coverage again; and rows given again, which keep the translated and placed rows they leave
/// out, and change or remove one only as the user confirms.
class TestRecordSourceTypesTool {

    private static final String BUILD = "pega-v2";
    private static final String FEED = "PEGA";
    private static final List<String> OTHERS = List.of("PegaIntegration = PegaIntegration", "PegaDB = PegaDB");

    private final SurveyDocuments surveyDocuments = Mockito.mock(SurveyDocuments.class);
    private final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
    private final BuildRecords buildRecords = Mockito.mock(BuildRecords.class);
    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    private final RecordSourceTypesTool tool = new RecordSourceTypesTool(surveyDocuments, registries, buildRecords,
            new Consent(() -> Mockito.mock(AiAuditLog.class)), plan);
    private final List<String> written = new ArrayList<>();

    private static final List<SourceTypeRows.Row> ROWS = List.of(
            new SourceTypeRows.Row("db", SourceTypeRows.VALUE, "db"),
            new SourceTypeRows.Row("web", SourceTypeRows.VALUE, "web"));

    @Test
    void aScopeWithinWhatEveryStreamWasReadForLeavesTheSurveyCovered() {
        // Read with no scope, or before scopes were kept: for every source type.
        final Map<String, Object> survey = survey(Map.of("2", List.of(SourceTypeScope.ALL)));

        assertThat(RecordSourceTypesTool.restartsCoverage(survey, scope(List.of("db", "web")))).isFalse();
        assertThat(survey).containsEntry("saturated", true).containsKey(SurveyDoc.ACCEPTED);
    }

    @Test
    void aScopeThatAddsSourceTypesAStreamWasntReadForStartsCoverageAgain() {
        final Map<String, Object> survey = survey(Map.of("2", List.of("db")));

        assertThat(RecordSourceTypesTool.restartsCoverage(survey, scope(List.of("db")))).isFalse();
        assertThat(RecordSourceTypesTool.restartsCoverage(survey, scope(List.of("db", "web")))).isTrue();
        assertThat(survey).containsEntry("saturated", false).doesNotContainKey(SurveyDoc.ACCEPTED);
    }

    @BeforeEach
    void setUp() {
        Mockito.when(plan.resolveBuild(any(), eq(BUILD), anyString())).thenReturn(BUILD);
        Mockito.when(plan.withNext(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.of(new SourceTypeRegistries.Found(
                new SurveyRecord("r", "PEGA source types", ""), false, registry())));
        Mockito.when(registries.writeRegistry(any(), eq(BUILD), eq(FEED), any(), anyString())).thenAnswer(inv -> {
            written.add(inv.getArgument(4));
            return new SurveyRecord("r", "PEGA source types", inv.getArgument(4));
        });
    }

    @Test
    void aTranslatedRowLeftOutOfTheRowsGivenIsKeptWithItsState() {
        final Map<String, Object> result = recorded(BuildToolCalls.context(), Map.of("rows", OTHERS));

        assertThat(result).as("%s", result).doesNotContainKey("error");
        final Map<String, Object> kept = rowNamed("PegaSecurityEvent");
        assertThat(kept).containsEntry("status", SourceTypeRegistry.TRANSLATED)
                .containsEntry("group", "PEGA security")
                .containsKey("group_xslt")
                .doesNotContainKey("output_feed");
        // The group's pipeline kept; the dispatcher's routing dropped.
        assertThat(JsonValues.mapOrEmpty(kept.get("pipeline"))).containsOnly(Map.entry("name", "PEGA PEGA security"),
                Map.entry("uuid", "p1"));
        // Kept where it was in the order: rows are tried in order.
        assertThat(rows().stream().map(row -> row.get("name")).toList()).containsExactly("PegaSecurityEvent",
                "PegaIntegration", "PegaDB");
    }

    @Test
    void aTranslatedRowIsRemovedOnlyAsTheUserConfirms() {
        final Map<String, Object> args = Map.of("rows", OTHERS, "remove", List.of("PegaSecurityEvent"));

        final Map<String, Object> refused = recorded(BuildToolCalls.context(), args);
        assertThat(String.valueOf(refused.get("error"))).contains("PegaSecurityEvent", "translated", "form");
        assertThat(written).isEmpty();

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> removed = recorded(user.context(), args);
        assertThat(removed).as("%s", removed).doesNotContainKey("error");
        assertThat(rows().stream().map(row -> row.get("name")).toList()).containsExactly("PegaIntegration",
                "PegaDB");
        assertThat(user.shown()).anySatisfy(message -> assertThat(message).contains("PegaSecurityEvent")
                .doesNotContain("**").doesNotContain("| "));
    }

    @Test
    void aTranslatedRowsChangedValueIsAskedAndARenameRefused() {
        final List<String> changed = new ArrayList<>(OTHERS);
        changed.addFirst("PegaSecurityEvent ~ ^PegaSecurity");
        assertThat(String.valueOf(recorded(BuildToolCalls.context(), Map.of("rows", changed)).get("error")))
                .contains("PegaSecurityEvent", "translated", "form");
        assertThat(written).isEmpty();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        assertThat(recorded(user.context(), Map.of("rows", changed))).doesNotContainKey("error");
        assertThat(rowNamed("PegaSecurityEvent")).containsEntry("match", SourceTypeRows.PATTERN)
                .containsEntry("value", "^PegaSecurity").containsEntry("status", SourceTypeRegistry.TRANSLATED);
        assertThat(user.shown()).anySatisfy(message -> assertThat(message).contains("^PegaSecurity"));

        final List<String> renamed = new ArrayList<>(OTHERS);
        renamed.addFirst("Security = PegaSecurityEvent");
        assertThat(String.valueOf(recorded(user.context(), Map.of("rows", renamed)).get("error")))
                .contains("rename", "PegaSecurityEvent", "Security", "remove");
    }

    // The tool called twice, as the agent does: the table shown, then the scope given.
    private Map<String, Object> recorded(final ToolCallContext context, final Map<String, Object> given) {
        final Map<String, Object> args = new LinkedHashMap<>(given);
        args.put("build", BUILD);
        args.put("feed", FEED);
        args.put("scope", List.of("PegaDB"));
        args.put("reason", "later");
        final Map<String, Object> shown = result(tool.execute(BuildToolCalls.call(RecordSourceTypesTool.NAME,
                JsonUtil.writeValueAsString(args, false)), context));
        if (shown.containsKey("error")) {
            return shown;
        }
        args.put("reviewed", shown.get("review_id"));
        return result(tool.execute(BuildToolCalls.call(RecordSourceTypesTool.NAME,
                JsonUtil.writeValueAsString(args, false)), context));
    }

    private static Map<String, Object> result(final ToolResult result) {
        return result.isError()
                ? Map.of("error", result.text())
                : BuildToolCalls.json(result.text());
    }

    private List<Map<String, Object>> rows() {
        return JsonValues.maps(SourceTypeRegistry.read(written.getLast()).get("rows"));
    }

    private Map<String, Object> rowNamed(final String name) {
        return rows().stream().filter(row -> name.equals(row.get("name"))).findFirst().orElseThrow();
    }

    // The build's registry: one source type translated, by a group in its pipeline; two parked.
    private static Map<String, Object> registry() {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", FEED);
        state.put("field", "sourcetype");
        final Map<String, Object> translated = row("PegaSecurityEvent", SourceTypeRegistry.TRANSLATED);
        translated.put("group", "PEGA security");
        translated.put("group_xslt", Map.of("uuid", "g1", "name", "PEGA security"));
        // Kept as a registry from before each group had a pipeline of its own: a dispatcher's routing with it.
        translated.put("pipeline", Map.of("own", false, "dispatcher", Map.of("uuid", "d1", "name",
                "PEGA dispatcher"), "name", "PEGA PEGA security", "uuid", "p1"));
        state.put("rows", List.of(translated, row("PegaIntegration", SourceTypeRegistry.PARKED),
                row("PegaDB", SourceTypeRegistry.PARKED)));
        return state;
    }

    private static Map<String, Object> row(final String name, final String status) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", SourceTypeRows.VALUE);
        row.put("value", name);
        row.put("status", status);
        return row;
    }

    private static SourceTypeScope scope(final List<String> names) {
        return new SourceTypeScope("b", "source", ROWS, names);
    }

    private static Map<String, Object> survey(final Map<String, Object> signed) {
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("streams_read", List.of(1L, 2L));
        survey.put("saturated", true);
        survey.put(SurveyDoc.ACCEPTED, Map.of("at", "now"));
        survey.put(SurveyDoc.SCOPE_READ, signed);
        return survey;
    }
}
