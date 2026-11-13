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
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
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
/// for starts the survey's coverage again; rows given again, which keep the translated and placed rows they leave
/// out, and change or remove one only as the user confirms; and the scope as the build's one source type group, its
/// source types and name chosen in one form (or given without forms), an existing group's name joining that group.
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

    @Test
    void theScopeFormChoosesTheGroupsSourceTypesAndItsName() {
        final GroupUser user = new GroupUser(List.of("PegaIntegration", "PegaDB"), "PEGA back end");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA integration"));

        assertThat(result).as("%s", result).doesNotContainKey("error")
                .containsEntry("group", "PEGA back end")
                .containsEntry("scope", List.of("PegaIntegration", "PegaDB"))
                .doesNotContainKey("joins");
        // One form: a yes per source type offered (not the translated one), and the group's name, suggested.
        assertThat(user.intros()).hasSize(1);
        assertThat(user.intros().getFirst()).contains("go in this build's source type group", "one group",
                        "parked for a later build", "Naming an existing group (PEGA security)")
                .doesNotContain("**").doesNotContain("| ");
        final Map<?, ?> properties = (Map<?, ?>) user.forms().getFirst().get("properties");
        assertThat(properties.values().stream().map(field -> String.valueOf(((Map<?, ?>) field).get("title")))
                .toList())
                .containsExactly("Put PegaIntegration in the group", "Put PegaDB in the group",
                        "What is the group called?");
        assertThat(((Map<?, ?>) properties.get("text")).get("default")).isEqualTo("PEGA integration");
        final Map<String, Object> scope = JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast())
                .get("scope"));
        assertThat(scope).containsEntry("build", BUILD).containsEntry("group", "PEGA back end")
                .containsEntry("source_types", List.of("PegaIntegration", "PegaDB"));
        assertThat(JsonValues.mapOrEmpty(rowNamed("PegaDB").get("decided")).get("reason")).asString()
                .contains("PEGA back end");
    }

    @Test
    void theSuggestedNameStandsWhereTheUserLeavesItAndTheRestIsParkedForLater() {
        final GroupUser user = new GroupUser(List.of("PegaDB"), "");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA databases"));

        assertThat(result).as("%s", result).containsEntry("group", "PEGA databases")
                .containsEntry("scope", List.of("PegaDB"));
        assertThat(rowNamed("PegaIntegration")).containsEntry("status", SourceTypeRegistry.PARKED);
        assertThat(JsonValues.mapOrEmpty(rowNamed("PegaIntegration").get("decided")).get("reason"))
                .isEqualTo("left for a later build");
    }

    @Test
    void aFormWithNoGroupToSuggestIsRefusedBeforeAnythingIsAsked() {
        final GroupUser user = new GroupUser(List.of("PegaDB"), "Anything");

        final Map<String, Object> refused = called(user.context(), Map.of());

        assertThat(String.valueOf(refused.get("error"))).contains("Give group", "PEGA security");
        assertThat(user.intros()).isEmpty();
        assertThat(written).isEmpty();
    }

    @Test
    void aGroupWithNoSourceTypeChosenIsRefused() {
        final GroupUser user = new GroupUser(List.of(), "PEGA databases");

        final Map<String, Object> refused = called(user.context(), Map.of("group", "PEGA databases"));

        assertThat(String.valueOf(refused.get("error"))).contains("No source type was chosen", "PEGA databases");
        assertThat(written).isEmpty();
    }

    @Test
    void anExistingGroupNamedWithNoSourceTypeChosenIsChangedAlone() {
        final GroupUser user = new GroupUser(List.of(), "PEGA security");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA security"));

        assertThat(result).as("%s", result).containsEntry("group", "PEGA security")
                .containsEntry("scope", List.of("PegaSecurityEvent"));
        assertThat(user.intros().getFirst()).contains("none ticked, changes it alone");
        assertThat(rowNamed("PegaDB")).containsEntry("status", SourceTypeRegistry.PARKED);
    }

    @Test
    void withoutFormsTheScopeTakesTheGroupsNameAndIsRefusedWithoutOne() {
        final Map<String, Object> refused = called(BuildToolCalls.context(), Map.of("scope",
                List.of("PegaDB", "PegaIntegration")));
        assertThat(String.valueOf(refused.get("error"))).contains("Give group", "one group");
        assertThat(written).isEmpty();

        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("scope",
                List.of("PegaDB", "PegaIntegration"), "group", "PEGA back end", "reason", "noisy"));

        assertThat(result).as("%s", result).containsEntry("group", "PEGA back end")
                .containsEntry("scope", List.of("PegaDB", "PegaIntegration"));
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("scope")))
                .containsEntry("group", "PEGA back end");
    }

    @Test
    void withoutFormsAndNoScopeTheUserIsAskedInTheChatForTheGroup() {
        final Map<String, Object> asked = called(BuildToolCalls.context(), Map.of());

        assertThat(asked).containsEntry("status", "needs_user")
                .containsEntry("offered", List.of("PegaIntegration", "PegaDB"))
                .containsEntry("groups", List.of("PEGA security"));
        assertThat(String.valueOf(asked.get("hint"))).contains("group=", "one group");
        assertThat(written).isEmpty();
    }

    @Test
    void withoutFormsAnEmptyScopeWithAnExistingGroupsNameChangesThatGroupAlone() {
        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("scope", List.of(),
                "group", "pega SECURITY", "reason", "later"));

        assertThat(result).as("%s", result).containsEntry("group", "PEGA security")
                .containsEntry("scope", List.of("PegaSecurityEvent"));
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("scope")))
                .containsEntry("group", "PEGA security")
                .containsEntry("source_types", List.of("PegaSecurityEvent"));
        assertThat(rowNamed("PegaDB")).containsEntry("status", SourceTypeRegistry.PARKED);
        assertThat(JsonValues.mapOrEmpty(rowNamed("PegaDB").get("decided"))).containsEntry("reason", "later");
        assertThat(rowNamed("PegaSecurityEvent")).containsEntry("status", SourceTypeRegistry.TRANSLATED);
    }

    @Test
    void withoutFormsAnEmptyScopeIsRefusedForANewGroupOrNoGroup() {
        final Map<String, Object> newGroup = called(BuildToolCalls.context(), Map.of("scope", List.of(),
                "group", "PEGA databases"));
        assertThat(String.valueOf(newGroup.get("error"))).contains("'PEGA databases' is a new one",
                "PEGA security");

        final Map<String, Object> noGroup = called(BuildToolCalls.context(), Map.of("scope", List.of()));
        assertThat(String.valueOf(noGroup.get("error"))).contains("Give group", "changes an existing");
        assertThat(written).isEmpty();
    }

    @Test
    void withFormsAnEmptyScopeIsNoAnswerAndTheFormIsShown() {
        final GroupUser user = new GroupUser(List.of("PegaDB"), "");

        final Map<String, Object> result = called(user.context(), Map.of("scope", List.of(),
                "group", "PEGA security"));

        assertThat(user.intros()).hasSize(1);
        assertThat(result).as("%s", result).containsEntry("scope", List.of("PegaSecurityEvent", "PegaDB"));
    }

    @Test
    void namingAnExistingGroupAddsTheSourceTypesToIt() {
        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("scope", List.of("PegaDB"),
                "group", "pega SECURITY"));

        // The existing group's own name, whatever the case given: its pipeline and output feed are the source
        // types' too once it is built.
        assertThat(result).as("%s", result).containsEntry("group", "PEGA security").containsKey("joins");
        assertThat(String.valueOf(result.get("joins"))).contains("PEGA security", "working copy");
        // The scope is the group: its own source type and the one added.
        assertThat(result).containsEntry("scope", List.of("PegaSecurityEvent", "PegaDB"));
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("scope")))
                .containsEntry("group", "PEGA security")
                .containsEntry("source_types", List.of("PegaSecurityEvent", "PegaDB"));
        // The translated row is not offered again, and stays as it is.
        assertThat(rowNamed("PegaSecurityEvent")).containsEntry("status", SourceTypeRegistry.TRANSLATED);
    }

    // The tool called twice, as the agent does: the table shown, then the scope given.
    private Map<String, Object> recorded(final ToolCallContext context, final Map<String, Object> given) {
        final Map<String, Object> args = new LinkedHashMap<>(given);
        args.put("scope", List.of("PegaDB"));
        args.put("group", "PEGA databases");
        args.put("reason", "later");
        return called(context, args);
    }

    // The tool called twice, as the agent does: the table shown, then called again with reviewed and these args.
    private Map<String, Object> called(final ToolCallContext context, final Map<String, Object> given) {
        final Map<String, Object> args = new LinkedHashMap<>(given);
        args.put("build", BUILD);
        args.put("feed", FEED);
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


    // --------------------------------------------------------------------------------


    /// A user of a client that shows forms, who answers the scope form: these source types in the group, and the
    /// group's name (blank to keep the suggestion).
    private static final class GroupUser implements UserQuestions {

        private final List<String> chosen;
        private final String name;
        private final List<String> intros = new ArrayList<>();
        private final List<Map<String, Object>> forms = new ArrayList<>();

        private GroupUser(final List<String> chosen, final String name) {
            this.chosen = chosen;
            this.name = name;
        }

        private ToolCallContext context() {
            return ToolCallContext.builder(BuildToolCalls.context().session())
                    .userQuestions(this)
                    .build();
        }

        private List<String> intros() {
            return intros;
        }

        private List<Map<String, Object>> forms() {
            return forms;
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            intros.add(intro);
            forms.add(form);
            final Map<String, Object> content = new LinkedHashMap<>();
            ((Map<?, ?>) form.get("properties")).forEach((key, field) -> {
                final Map<?, ?> definition = (Map<?, ?>) field;
                if ("boolean".equals(definition.get("type"))) {
                    content.put(String.valueOf(key), chosen.stream().anyMatch(type -> ("Put " + type
                                                                                       + " in the group").equals(
                            definition.get("title"))));
                } else {
                    content.put(String.valueOf(key), name.isBlank()
                            ? definition.get("default")
                            : name);
                }
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content);
        }
    }
}
