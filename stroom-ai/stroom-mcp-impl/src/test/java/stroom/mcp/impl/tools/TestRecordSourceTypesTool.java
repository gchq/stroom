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
import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeProfiles;
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

    private static final String PAIR = "system + event>logger_name";

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

        // Agrees to the change, and ticks a source type in the scope form (scope is the form's where one can show).
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true, "picked",
                List.of("PegaIntegration")));
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
        // Agrees to the change, and ticks a source type in the scope form (scope is the form's where one can show).
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true, "picked",
                List.of("PegaIntegration")));
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
    void aScopeOfManySourceTypesIsOneFormWithinWhatAFormHolds() {
        // Seen in a real run: 65 source types, a yes/no field each, too many for a form, so it was never shown.
        parked(65, "Source type ");
        final GroupUser user = new GroupUser(List.of("Source type 3", "Source type 64"), "Many");

        final Map<String, Object> result = called(user.context(), Map.of("group", "Many"));

        assertThat(user.forms()).as("the scope form is shown").hasSize(1);
        assertThat(result).as("%s", result).doesNotContainKey("error")
                .containsEntry("scope", List.of("Source type 3", "Source type 64"))
                .containsEntry("group", "Many");
    }

    @Test
    void theScopeSaidInTheChatIsTakenOnceItsFormCouldntBeShown() {
        // Seen in a real run: the scope form couldn't be shown, the agent was told to ask in the chat and pass
        // scope=, and the scope it passed was refused (the client shows forms), so the form was asked again, for
        // ever.
        final String prefix = "A source type with a name long enough to make its form too large to show, number ";
        parked(300, prefix);
        final GroupUser user = new GroupUser(List.of(), "Any");

        final Map<String, Object> asked = called(user.context(), Map.of("group", "Many"));

        assertThat(asked).as("%s", asked).containsEntry("status", "needs_user");
        assertThat((String) asked.get("hint")).contains("The form was too large to show", "scope=[their names]");
        assertThat(user.forms()).isEmpty();
        final Map<String, Object> result = called(user.context(), Map.of("group", "Many", "scope",
                List.of(prefix + 7, prefix + 9)));
        assertThat(result).as("%s", result).doesNotContainKey("error")
                .containsEntry("scope", List.of(prefix + 7, prefix + 9))
                .containsEntry("group", "Many");
        assertThat(user.forms()).isEmpty();
    }

    @Test
    void aQuestionTypedInThePatternsFormIsNotTakenForUsingThePatterns() {
        // Seen in testing: a question typed in a form's own-words box was matched as an answer by the words it
        // happened to hold ("pattern", "item", "use", "one"...), so the user's question was taken for their answer.
        patternValues();
        final String question = "which pattern would match the SOAP events?";
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", question));

        final Map<String, Object> result = once(user.context(), Map.of());

        assertThat(user.shown()).as("the patterns form is shown").first().asString()
                .contains("65 source types are proposed");
        assertThat(result).as("%s", result).doesNotContainEntry("status", "needs_review")
                .doesNotContainKey("rows");
        assertThat(String.valueOf(result)).contains(question).containsIgnoringCase("question");
    }

    @Test
    void aQuestionTypedInThePatternsFormIsNotTakenForOneSourceTypePerValue() {
        // As above: a question starting "one ..." taken for "One source type per value".
        patternValues();
        final String question = "One thing first: what does a pattern row match on?";
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", question));

        final Map<String, Object> result = once(user.context(), Map.of());

        assertThat(result).as("%s", result).doesNotContainEntry("status", "needs_review")
                .doesNotContainKey("rows");
        assertThat(String.valueOf(result)).contains(question).containsIgnoringCase("question");
    }

    @Test
    void anOptionTypedInThePatternsFormWholeIsTakenWhateverItsCase() {
        patternValues();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "  use THESE patterns "));

        final Map<String, Object> result = once(user.context(), Map.of());

        assertThat(result).as("%s", result).containsEntry("status", "needs_review");
        assertThat((List<?>) result.get("rows")).hasSize(65);
    }

    @Test
    void aQuestionTypedInThePatternsFormIsAnsweredInTheChatItsFormNotShownAgain() {
        // As for a form put aside by a message: the next call takes patterns= as the user's chat answer, and a call
        // without it is told to pass it, the form not shown again.
        patternValues();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "which pattern is which?"));
        once(user.context(), Map.of());
        assertThat(user.shown()).hasSize(1);

        final Map<String, Object> without = once(user.context(), Map.of());

        assertThat(user.shown()).as("forms shown").hasSize(1);
        assertThat(without).as("%s", without).containsEntry("status", "needs_user");
        assertThat(String.valueOf(without.get("hint"))).contains("put it aside", "patterns='use' or 'exact'");

        final Map<String, Object> given = once(user.context(), Map.of("patterns", "exact"));

        assertThat(user.shown()).as("forms shown").hasSize(1);
        assertThat(given).as("%s", given).containsEntry("status", "needs_review");
        assertThat((List<?>) given.get("rows")).hasSize(130);
    }

    @Test
    void wordsThatHoldAnAnswerAsPatternsAreRefusedNotTaken() {
        patternValues();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "unused"));

        final Map<String, Object> result = once(user.context(), Map.of("patterns", "one of the patterns"));

        assertThat(String.valueOf(result.get("error"))).contains("is neither 'use'");
        assertThat(user.shown()).isEmpty();
    }

    @Test
    void manyPatternsAreAskedInAFormWithinWhatAFormHolds() {
        // Seen in a real run: a line a pattern in the form's message, past what a message holds.
        final Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i <= 65; i++) {
            values.put("/var/log/application" + i + "/audit.log", Map.of("records", 100 + i));
            values.put("/var/log/application" + i + "/access.log", Map.of("records", 50 + i));
        }
        surveyed("file_path", values);
        final GroupUser user = new GroupUser(List.of(), "");

        final Map<String, Object> result = once(user.context(), Map.of());

        assertThat(user.intros()).as("the patterns form is shown").singleElement().asString()
                .contains("65 source types are proposed", "And 60 more (all are in the table shown in the chat).");
        assertThat(result).as("%s", result).containsEntry("status", "needs_review");
        assertThat((List<?>) result.get("rows")).hasSize(65);
    }

    @Test
    void aScopeTheAgentGivesDoesNotSkipTheFormWhereOneCanBeShown() {
        // Seen in an eval: scope=["OneDrive"] passed with forms available wrote the group unasked.
        final GroupUser user = new GroupUser(List.of("PegaIntegration", "PegaDB"), "PEGA back end");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA integration",
                "scope", List.of("PegaDB")));

        assertThat(user.intros()).as("the scope form is asked").hasSize(1);
        assertThat(result).as("%s", result).doesNotContainKey("error")
                .containsEntry("group", "PEGA back end")
                .containsEntry("scope", List.of("PegaIntegration", "PegaDB"));
    }

    @Test
    void theScopeFormChoosesTheGroupsSourceTypesAndItsName() {
        final GroupUser user = new GroupUser(List.of("PegaIntegration", "PegaDB"), "PEGA back end");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA integration"));

        assertThat(result).as("%s", result).doesNotContainKey("error")
                .containsEntry("group", "PEGA back end")
                .containsEntry("scope", List.of("PegaIntegration", "PegaDB"))
                .doesNotContainKey("joins");
        // One form: a list of the source types offered (not the translated one) to tick, and the group's name,
        // suggested.
        assertThat(user.intros()).hasSize(1);
        assertThat(user.intros().getFirst()).contains("go in this build's source type group", "one group",
                        "parked for a later build", "Naming an existing group (PEGA security)")
                .doesNotContain("**").doesNotContain("| ");
        final Map<?, ?> properties = (Map<?, ?>) user.forms().getFirst().get("properties");
        assertThat(properties.values().stream().map(field -> String.valueOf(((Map<?, ?>) field).get("title")))
                .toList())
                .containsExactly(RecordSourceTypesTool.SCOPE_QUESTION, "What is the group called?");
        assertThat(((Map<?, ?>) properties.get("text")).get("default")).isEqualTo("PEGA integration");
        final Map<?, ?> list = (Map<?, ?>) properties.get("picked");
        assertThat(GroupUser.options(list)).containsExactly("PegaIntegration", "PegaDB");
        // Each with its share and formats, as the table shows them.
        assertThat((List<?>) ((Map<?, ?>) list.get("items")).get("optionDescriptions")).hasSize(2)
                .allSatisfy(description -> assertThat(description).asString().contains("% of the records read"));
        // No source type ticked to start: the user picks the group's.
        assertThat(list.get("default")).isEqualTo(List.of());
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
    void theScopeChosenInTheFormIsSaidToBeTheUsersSoTheAgentDoesNotAskAgain() {
        // Seen in chat 181: the result said only "the scope recorded", and the agent took the user's form answer for
        // a choice made without them, and asked again.
        final GroupUser user = new GroupUser(List.of("PegaDB"), "PEGA back end");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA back end"));

        assertThat(result).as("%s", result).containsEntry("chosen_by", "user");
        assertThat(String.valueOf(result.get("hint"))).contains("the scope the user chose", "Don't ask them again",
                "stay parked until the group is built");
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
        assertThat(String.valueOf(result.get("joins"))).contains("PEGA security", "new version of its XSLT");
        // The scope is the group: its own source type and the one added.
        assertThat(result).containsEntry("scope", List.of("PegaSecurityEvent", "PegaDB"));
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("scope")))
                .containsEntry("group", "PEGA security")
                .containsEntry("source_types", List.of("PegaSecurityEvent", "PegaDB"));
        // The translated row is not offered again, and stays as it is.
        assertThat(rowNamed("PegaSecurityEvent")).containsEntry("status", SourceTypeRegistry.TRANSLATED);
    }

    @Test
    void aNominatedRowTheSurveyHasntMetIsOfferedToBeFound() {
        final List<String> rows = new ArrayList<>(OTHERS);
        rows.add("PowerBI = PowerBI");

        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("rows", rows, "scope",
                List.of("PegaDB", "PowerBI"), "group", "PEGA back end"));

        assertThat(result).as("%s", result).containsEntry("unseen_source_types", List.of("PowerBI"));
        assertThat(String.valueOf(result.get("hint"))).contains("read no record of [PowerBI] yet",
                "two ways to find one", "survey_feed feed='PEGA' build='pega-v2' stream_ids=[<id>] find='<name>'",
                "survey_feed feed='PEGA' build='pega-v2' find='<name>'");
        assertThat(JsonValues.mapOrEmpty(rowNamed("PowerBI").get("profile"))).containsEntry("records_read", 0L);
    }

    @Test
    void onlyRowsNominatedOrInTheGroupAndNotDroppedAreUnseen() {
        final Map<String, Object> registry = Map.of("rows", List.of(
                profiled("New", 0, null), profiled("Parked", 0, SourceTypeRegistry.PARKED),
                profiled("Grouped", 0, SourceTypeRegistry.PARKED), profiled("Dropped", 0, SourceTypeRegistry.DROPPED),
                profiled("Seen", 5, null)));
        final Map<String, Object> before = Map.of("rows", List.of(row("Parked", SourceTypeRegistry.PARKED),
                row("Grouped", SourceTypeRegistry.PARKED), row("Dropped", SourceTypeRegistry.PARKED)));

        assertThat(RecordSourceTypesTool.unseen(registry, before, List.of("Grouped"))).containsExactly("New",
                "Grouped");
        assertThat(RecordSourceTypesTool.unseen(registry, before, List.of())).containsExactly("New");
        // Seen, nothing offered.
        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("rows", OTHERS, "scope",
                List.of("PegaDB"), "group", "PEGA back end"));
        assertThat(result).as("%s", result).doesNotContainKey("unseen_source_types");
    }

    @Test
    void aListMemberRowIsWrittenWithItsSeparatorAndShownWithItsSign() {
        final List<String> rows = new ArrayList<>(OTHERS);
        rows.add("Directory in Directory Services");

        final Map<String, Object> result = recorded(BuildToolCalls.context(), Map.of("rows", rows));

        assertThat(result).as("%s", result).doesNotContainKey("error");
        assertThat(rowNamed("Directory")).containsEntry("match", SourceTypeRows.MEMBER)
                .containsEntry("value", "Directory Services").containsEntry("separator", ",");
        assertThat(written.getLast()).contains("∈ Directory Services", "an item of the value read as a list");
        assertThat(SourceTypeRegistry.rows(SourceTypeRegistry.read(written.getLast()))).contains(
                new SourceTypeRows.Row("Directory", SourceTypeRows.MEMBER, "Directory Services", ","));
        // The reply's rows say each match as the table and the registry do. Seen (eval 59): a list-member row given
        // back to the agent as '= Active Directory Domain Services', a value it isn't.
        assertThat(JsonValues.maps(result.get("rows")))
                .extracting(row -> row.get("name") + " " + row.get("match"))
                .contains("Directory ∈ Directory Services", "PegaDB = PegaDB")
                .noneMatch(said -> said.startsWith("Directory ="));
        assertThat((String) result.get("table")).contains("| ∈ Directory Services |");
    }

    @Test
    void aPatternRowIsGivenBackWithItsSign() {
        final List<String> rows = new ArrayList<>(OTHERS);
        rows.add("Mail ~ ^/var/log/mail/");

        final Map<String, Object> result = recorded(BuildToolCalls.context(), Map.of("rows", rows));

        assertThat(result).as("%s", result).doesNotContainKey("error");
        assertThat(JsonValues.maps(result.get("rows")))
                .extracting(row -> row.get("name") + " " + row.get("match"))
                .contains("Mail ~ ^/var/log/mail/", "PegaDB = PegaDB");
    }

    @Test
    void valuesSeenThatNoListRowMatchesAreGivenARowForTheirRarestItem() {
        final List<SourceTypeRows.Row> rows = List.of(new SourceTypeRows.Row("Directory", SourceTypeRows.MEMBER,
                "Directory Services", ","));
        final Map<String, Long> values = new LinkedHashMap<>();
        values.put("DNS Server, File Server", 5L);
        values.put("File Server", 3L);
        values.put("Directory Services, DNS Server", 2L);

        final List<SourceTypeRows.Row> seen = RecordSourceTypesTool.withValuesSeen(rows, values);

        assertThat(seen).extracting(row -> row.name() + " " + row.said()).containsExactly(
                "Directory ∈ Directory Services", "DNS Server ∈ DNS Server", "File Server ∈ File Server");
        assertThat(SourceTypeRows.problems(seen, values.keySet())).isEmpty();
        // Rows of whole values where the registry's rows aren't a list's items.
        assertThat(RecordSourceTypesTool.withValuesSeen(ROWS, Map.of("other", 1L))).last()
                .isEqualTo(new SourceTypeRows.Row("other", SourceTypeRows.VALUE, "other"));
    }

    @Test
    void aSettingsOnlyCallOnceTheScopeIsRecordedAsksNeitherTheTableNorTheScopeForm() {
        // Seen in chat 178: environment and system_names recorded from earlier answers asked the whole scope form.
        scoped("PEGA back end", List.of("PegaIntegration", "PegaDB"));
        final GroupUser user = new GroupUser(List.of("PegaDB"), "Other");

        final Map<String, Object> result = once(user.context(), Map.of("group", "PEGA back end",
                "environment", "Test", "system_names", Map.of("PegaDB", "Pega Database")));

        assertThat(result).as("%s", result).doesNotContainKey("error")
                .doesNotContainEntry("status", "needs_review")
                .containsEntry("group", "PEGA back end")
                .containsEntry("scope", List.of("PegaIntegration", "PegaDB"));
        assertThat(user.intros()).as("no form").isEmpty();
        final Map<String, Object> registry = SourceTypeRegistry.read(written.getLast());
        assertThat(registry).containsEntry("environment", "Test");
        assertThat(rowNamed("PegaDB")).containsEntry("system_name", "Pega Database")
                .containsEntry("status", SourceTypeRegistry.PARKED);
        assertThat(JsonValues.mapOrEmpty(registry.get("scope"))).containsEntry("build", BUILD)
                .containsEntry("group", "PEGA back end")
                .containsEntry("source_types", List.of("PegaIntegration", "PegaDB"));
        assertThat(String.valueOf(result.get("hint"))).contains("isn't asked again", "next");

        // No group given at all: the recorded one stands.
        final Map<String, Object> noGroup = once(user.context(), Map.of("environment", "Prod"));
        assertThat(noGroup).as("%s", noGroup).containsEntry("group", "PEGA back end");
        assertThat(user.intros()).isEmpty();
    }

    @Test
    void unchangedRowsGivenAgainOnceTheScopeIsRecordedAskNothing() {
        scoped("PEGA back end", List.of("PegaIntegration", "PegaDB"));
        final GroupUser user = new GroupUser(List.of("PegaDB"), "Other");

        // The translated row left out is kept in its place: the rows come to the registry's.
        final Map<String, Object> result = once(user.context(), Map.of("rows", OTHERS, "group", "PEGA back end",
                "scope", List.of("PegaDB", "pegaintegration")));

        assertThat(result).as("%s", result).doesNotContainKey("error")
                .doesNotContainEntry("status", "needs_review")
                .containsEntry("scope", List.of("PegaIntegration", "PegaDB"));
        assertThat(user.intros()).isEmpty();
        assertThat(written).hasSize(1);
    }

    @Test
    void anotherGroupNameOnceTheScopeIsRecordedIsARenameTheUserConfirms() {
        // Seen in chat 178: a typo fixed in the group's name asked the whole scope form again.
        scoped("PEGA bak end", List.of("PegaIntegration", "PegaDB"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> result = once(user.context(), Map.of("rows", OTHERS, "group", "PEGA back end"));

        assertThat(result).as("%s", result).doesNotContainKey("error")
                .containsEntry("group", "PEGA back end")
                .containsEntry("renamed_from", "PEGA bak end")
                .containsEntry("scope", List.of("PegaIntegration", "PegaDB"));
        assertThat(user.shown()).hasSize(1);
        assertThat(user.shown().getFirst()).contains("Rename group 'PEGA bak end' to 'PEGA back end'?")
                .doesNotContain("go in this build's source type group");
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("scope")))
                .containsEntry("group", "PEGA back end")
                .containsEntry("source_types", List.of("PegaIntegration", "PegaDB"));
        assertThat(JsonValues.mapOrEmpty(rowNamed("PegaDB").get("decided")).get("reason")).asString()
                .contains("'PEGA back end'");
    }

    @Test
    void aRenameDeclinedWritesNothingAndWithoutFormsTheNameGivenIsTheUsers() {
        scoped("PEGA bak end", List.of("PegaIntegration", "PegaDB"));

        final Map<String, Object> declined = once(BuildToolCalls.declining().context(), Map.of("group",
                "PEGA back end"));
        assertThat(declined).containsKey("error");
        assertThat(written).isEmpty();

        final Map<String, Object> renamed = once(BuildToolCalls.context(), Map.of("group", "PEGA back end"));
        assertThat(renamed).as("%s", renamed).containsEntry("group", "PEGA back end")
                .containsEntry("renamed_from", "PEGA bak end");
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("scope")))
                .containsEntry("group", "PEGA back end");
    }

    @Test
    void aBuiltOrPromotedGroupIsNotRenamed() {
        // The build joined the existing group: its XSLT and pipeline carry its name.
        scoped("PEGA security", List.of("PegaSecurityEvent", "PegaDB"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> refused = once(user.context(), Map.of("group", "PEGA sec"));

        assertThat(String.valueOf(refused.get("error"))).contains("'PEGA security'", "keeps its name",
                "Nothing was written");
        assertThat(user.shown()).isEmpty();
        assertThat(written).isEmpty();
    }

    @Test
    void otherSourceTypesOnceTheScopeIsRecordedAskTheScopeFormAgain() {
        scoped("PEGA back end", List.of("PegaIntegration", "PegaDB"));
        final GroupUser user = new GroupUser(List.of("PegaDB"), "");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA back end",
                "scope", List.of("PegaDB")));

        assertThat(user.intros()).as("the scope form").hasSize(1);
        assertThat(user.intros().getFirst()).contains("go in this build's source type group");
        assertThat(result).as("%s", result).containsEntry("scope", List.of("PegaDB"));
    }

    @Test
    void anotherExistingGroupNamedOnceTheScopeIsRecordedAsksTheScopeFormAgain() {
        scoped("PEGA back end", List.of("PegaIntegration", "PegaDB"));
        final GroupUser user = new GroupUser(List.of("PegaDB"), "");

        final Map<String, Object> shown = once(user.context(), Map.of("group", "PEGA security"));
        assertThat(shown).containsEntry("status", "needs_review");

        final Map<String, Object> result = called(user.context(), Map.of("group", "PEGA security"));
        assertThat(user.intros()).hasSize(1);
        assertThat(result).as("%s", result).containsEntry("scope", List.of("PegaSecurityEvent", "PegaDB"));
    }

    @Test
    void changedRowsOnceTheScopeIsRecordedAreReviewedAgain() {
        scoped("PEGA back end", List.of("PegaIntegration", "PegaDB"));
        final List<String> rows = new ArrayList<>(OTHERS);
        rows.add("PowerBI = PowerBI");

        final Map<String, Object> shown = once(BuildToolCalls.context(), Map.of("rows", rows,
                "group", "PEGA back end"));

        assertThat(shown).as("%s", shown).containsEntry("status", "needs_review");
        assertThat(String.valueOf(shown.get("table"))).contains("PowerBI");
        assertThat(written).isEmpty();
    }

    @Test
    void recordsWithNoValueAreOfferedTheNoValueRowLastAndItMayBeInTheScope() {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("com.x.Auth", Map.of("records", 5));
        values.put("com.x.Db", Map.of("records", 4));
        values.put("", Map.of("records", 3));
        final Map<String, Object> sources = new LinkedHashMap<>();
        sources.put(SourceTypeProfiles.FIELD, "event>logger_name | event>xml:@logger");
        sources.put(SourceTypeProfiles.BY, "user");
        sources.put(SourceTypeProfiles.VALUES, values);
        sources.put("records", 12);
        final Map<String, Object> survey = new LinkedHashMap<>(Map.of("feed", FEED, "format", "json lines",
                "streams_read", List.of(1), "surveys", List.of(), "shapes", Map.of()));
        survey.put(SourceTypeProfiles.STATE_KEY, sources);
        final String text = SurveyDoc.render(survey);
        Mockito.when(surveyDocuments.load(BUILD, FEED)).thenReturn(Optional.of(new SurveyRecord("s",
                "PEGA - Survey", text)));
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.empty());

        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("scope", List.of("com.x.Auth",
                SourceTypeRows.NO_VALUE), "group", "Held", "reason", "later", "patterns", "exact"));

        assertThat(result).as("%s", result).doesNotContainKey("error");
        assertThat(rows()).extracting(row -> row.get("name")).containsExactly("com.x.Auth", "com.x.Db",
                SourceTypeRows.NO_VALUE);
        assertThat(rowNamed(SourceTypeRows.NO_VALUE)).containsEntry("match", SourceTypeRows.NONE)
                .containsEntry("value", "");
        assertThat(SourceTypeRegistry.read(written.getLast())).containsEntry("field",
                "event>logger_name | event>xml:@logger");
        assertThat(written.getLast()).contains("logger_name inside the held field event", "| no value |");
    }

    @Test
    void aCombinationsRowsAreThoseProposedWithItAKeyTheyMissExactlyAndNoValueLast() {
        surveyedByPair(List.of(new SourceTypeRows.Row("Teams", SourceTypeRows.TUPLE, "= Teams + *").toMap(),
                new SourceTypeRows.Row("Skype System", SourceTypeRows.TUPLE, "= Skype + = System").toMap()));

        final Map<String, Object> result = called(BuildToolCalls.context(), Map.of("scope", List.of("Teams"),
                "group", "UC", "reason", "later"));

        assertThat(result).as("%s", result).doesNotContainKey("error");
        // The pair's rows, a key they miss matched exactly, then (no value) for the keys with a part of none.
        assertThat(rows()).extracting(row -> row.get("name") + ": " + row.get("value")).containsExactly(
                "Teams: = Teams + *", "Skype System: = Skype + = System",
                "Skype Application: = Skype + = Application", SourceTypeRows.NO_VALUE + ": ");
        assertThat(rowNamed("Teams")).containsEntry("match", SourceTypeRows.TUPLE);
        final Map<String, Object> registry = SourceTypeRegistry.read(written.getLast());
        assertThat(registry).containsEntry("field", PAIR);
        assertThat(written.getLast()).contains("told apart by the combination", "= Teams + *",
                "A row matches each part in turn");
        // The Teams key with a logger of no value is taken by Teams' * row; Skype's falls to (no value).
        assertThat(JsonValues.mapOrEmpty(rowNamed("Teams").get("profile")).get("records_read")).isEqualTo(9L);
        assertThat(JsonValues.mapOrEmpty(rowNamed(SourceTypeRows.NO_VALUE).get("profile")).get("records_read"))
                .isEqualTo(2L);
    }

    @Test
    void aCombinationsRowsGivenAreTuplesAndOnesThatAreNoneAreRefused() {
        surveyedByPair(List.of());

        final Map<String, Object> given = called(BuildToolCalls.context(), Map.of("rows", List.of(
                "Teams System = Teams + System", "Teams = Teams + *", "Skype ~ ^Sk + *", SourceTypeRows.NO_VALUE),
                "scope", List.of("Teams"), "group", "UC", "reason", "later"));
        assertThat(given).as("%s", given).doesNotContainKey("error");
        assertThat(rows()).extracting(row -> row.get("value")).containsExactly("= Teams + = System",
                "= Teams + *", "~ ^Sk + *", "");

        assertThat(called(BuildToolCalls.context(), Map.of("rows", List.of("Teams = Teams"), "scope",
                List.of("Teams"), "group", "UC", "reason", "later")).get("error"))
                .asString().contains("gives 1 part(s)", "combination of 2");
        assertThat(called(BuildToolCalls.context(), Map.of("rows", List.of("All = * + *"), "scope",
                List.of("All"), "group", "UC", "reason", "later")).get("error"))
                .asString().contains("every part is '*'");
        assertThat(called(BuildToolCalls.context(), Map.of("rows", List.of("Teams Teams"), "scope",
                List.of("Teams"), "group", "UC", "reason", "later")).get("error"))
                .asString().contains("'name = part + part'");
        // Without rows kept with the pair: a row a key whose parts all have a value.
        final Map<String, Object> proposed = called(BuildToolCalls.context(), Map.of("scope", List.of("Teams System"),
                "group", "UC", "reason", "later"));
        assertThat(proposed).as("%s", proposed).doesNotContainKey("error");
    }

    @Test
    void theRowsANeedsReviewReplyGivesBackAreTakenAsTheyAreTheNoValueRowToo() {
        // Regression: the reply gave the (no value) row back as '(no value) no value', which the next call refused.
        surveyedByPair(List.of());
        final Map<String, Object> args = new LinkedHashMap<>(Map.of("build", BUILD, "feed", FEED));
        final Map<String, Object> shown = result(tool.execute(BuildToolCalls.call(RecordSourceTypesTool.NAME,
                JsonUtil.writeValueAsString(args, false)), BuildToolCalls.context()));
        final List<String> given = JsonValues.strings(shown.get("rows"));
        assertThat(given).contains(SourceTypeRows.NO_VALUE, "Teams System = Teams + = System");

        final Map<String, Object> again = called(BuildToolCalls.context(), Map.of("rows", given, "scope",
                List.of("Teams System"), "group", "UC", "reason", "later"));

        assertThat(again).as("%s", again).doesNotContainKey("error");
        assertThat(rows()).extracting(row -> row.get("name")).containsExactly("Teams System", "Skype System",
                "Teams Application", "Skype Application", SourceTypeRows.NO_VALUE);
    }

    @Test
    void theNoValueRowOfOneReadingIsGivenBackAsItIsWritten() {
        // Regression, one reading: '(no value) no value' was refused when given back.
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("com.x.Auth", Map.of("records", 5));
        values.put("", Map.of("records", 3));
        final Map<String, Object> sources = new LinkedHashMap<>();
        sources.put(SourceTypeProfiles.FIELD, "event>logger_name");
        sources.put(SourceTypeProfiles.BY, "user");
        sources.put(SourceTypeProfiles.VALUES, values);
        sources.put("records", 8);
        final Map<String, Object> survey = new LinkedHashMap<>(Map.of("feed", FEED, "format", "json lines",
                "streams_read", List.of(1), "surveys", List.of(), "shapes", Map.of()));
        survey.put(SourceTypeProfiles.STATE_KEY, sources);
        Mockito.when(surveyDocuments.load(BUILD, FEED)).thenReturn(Optional.of(new SurveyRecord("s",
                "PEGA - Survey", SurveyDoc.render(survey))));
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.empty());
        final Map<String, Object> shown = result(tool.execute(BuildToolCalls.call(RecordSourceTypesTool.NAME,
                JsonUtil.writeValueAsString(Map.of("build", BUILD, "feed", FEED), false)), BuildToolCalls.context()));

        final Map<String, Object> again = called(BuildToolCalls.context(), Map.of("rows", shown.get("rows"),
                "scope", List.of("com.x.Auth"), "group", "Held", "reason", "later"));

        assertThat(again).as("%s", again).doesNotContainKey("error");
        assertThat(rows()).extracting(row -> row.get("name")).containsExactly("com.x.Auth", SourceTypeRows.NO_VALUE);
    }

    // A survey of the feed by the pair `system + event>logger_name`: its keys counted (two with a part of no value),
    // and the rows proposed with the pair kept, if any.
    private void surveyedByPair(final List<Map<String, Object>> proposed) {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("Teams ‖ System", Map.of("records", 5));
        values.put("Teams ‖ Application", Map.of("records", 3));
        values.put("Teams ‖ ", Map.of("records", 1));
        values.put("Skype ‖ System", Map.of("records", 4));
        values.put("Skype ‖ Application", Map.of("records", 2));
        values.put("Skype ‖ ", Map.of("records", 2));
        final Map<String, Object> sources = new LinkedHashMap<>();
        sources.put(SourceTypeProfiles.FIELD, PAIR);
        sources.put(SourceTypeProfiles.BY, "user");
        sources.put(SourceTypeProfiles.VALUES, values);
        sources.put("records", 17);
        if (!proposed.isEmpty()) {
            sources.put(SourceTypeProfiles.ROWS_PROPOSED, proposed);
        }
        final Map<String, Object> survey = new LinkedHashMap<>(Map.of("feed", FEED, "format", "json lines",
                "streams_read", List.of(1), "surveys", List.of(), "shapes", Map.of()));
        survey.put(SourceTypeProfiles.STATE_KEY, sources);
        Mockito.when(surveyDocuments.load(BUILD, FEED)).thenReturn(Optional.of(new SurveyRecord("s",
                "PEGA - Survey", SurveyDoc.render(survey))));
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.empty());
    }

    // The build's registry: so many source types, all parked, named so with a number.
    private void parked(final int count, final String prefix) {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", FEED);
        state.put("field", "sourcetype");
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(row(prefix + i, SourceTypeRegistry.PARKED));
        }
        state.put("rows", rows);
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.of(new SourceTypeRegistries.Found(
                new SurveyRecord("r", "PEGA source types", ""), false, state)));
    }

    // The build's survey, by this field, having seen these values (each with its records), and no registry.
    // A survey of many paths, two of each of 65 applications: patterns are proposed, and put to the user.
    private void patternValues() {
        final Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i <= 65; i++) {
            values.put("/var/log/application" + i + "/audit.log", Map.of("records", 100 + i));
            values.put("/var/log/application" + i + "/access.log", Map.of("records", 50 + i));
        }
        surveyed("file_path", values);
    }

    private void surveyed(final String field, final Map<String, Object> values) {
        final Map<String, Object> sources = new LinkedHashMap<>();
        sources.put(SourceTypeProfiles.FIELD, field);
        sources.put(SourceTypeProfiles.BY, "user");
        sources.put(SourceTypeProfiles.VALUES, values);
        sources.put("records", 1_000);
        final Map<String, Object> survey = new LinkedHashMap<>(Map.of("feed", FEED, "format", "json lines",
                "streams_read", List.of(1), "surveys", List.of(), "shapes", Map.of()));
        survey.put(SourceTypeProfiles.STATE_KEY, sources);
        Mockito.when(surveyDocuments.load(BUILD, FEED)).thenReturn(Optional.of(new SurveyRecord("s",
                "PEGA - Survey", SurveyDoc.render(survey))));
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.empty());
    }

    private static Map<String, Object> profiled(final String name, final long records, final String status) {
        final Map<String, Object> row = row(name, status);
        row.put("profile", Map.of("records_read", records));
        return row;
    }

    // The build's registry with this build's scope recorded: the group and its source types (those not translated
    // parked in it).
    private void scoped(final String group, final List<String> sourceTypes) {
        final Map<String, Object> state = registry();
        JsonValues.maps(state.get("rows")).stream()
                .filter(row -> sourceTypes.contains(row.get("name"))
                               && SourceTypeRegistry.PARKED.equals(row.get("status")))
                .forEach(row -> row.put("decided", Map.of("by", "user", "at", "then", "reason",
                        "in the scope of build '" + BUILD + "': translated once its source type group '" + group
                        + "' is built")));
        state.put("scope", Map.of("build", BUILD, "group", group, "source_types", sourceTypes, "by", "user",
                "at", "then"));
        Mockito.when(registries.registry(BUILD, FEED)).thenReturn(Optional.of(new SourceTypeRegistries.Found(
                new SurveyRecord("r", "PEGA source types", ""), false, state)));
    }

    // The tool called once, with these args.
    private Map<String, Object> once(final ToolCallContext context, final Map<String, Object> given) {
        final Map<String, Object> args = new LinkedHashMap<>(given);
        args.put("build", BUILD);
        args.put("feed", FEED);
        return result(tool.execute(BuildToolCalls.call(RecordSourceTypesTool.NAME,
                JsonUtil.writeValueAsString(args, false)), context));
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
            // Stroom shows no form too large to show: a tool asking one has failed.
            assertThat(ElicitationSchema.check(intro, form)).as("a form too large to show: %s", intro).isNull();
            intros.add(intro);
            forms.add(form);
            final Map<String, Object> content = new LinkedHashMap<>();
            ((Map<?, ?>) form.get("properties")).forEach((key, field) -> {
                final Map<?, ?> definition = (Map<?, ?>) field;
                if ("array".equals(definition.get("type"))) {
                    content.put(String.valueOf(key), options(definition).stream().filter(chosen::contains).toList());
                } else if ("boolean".equals(definition.get("type"))) {
                    content.put(String.valueOf(key), true);
                } else if (definition.get("examples") instanceof final List<?> suggested) {
                    // A question with answers suggested: the first.
                    content.put(String.valueOf(key), suggested.getFirst());
                } else {
                    content.put(String.valueOf(key), name.isBlank()
                            ? definition.get("default")
                            : name);
                }
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content);
        }

        // A list's options, by value.
        private static List<String> options(final Map<?, ?> list) {
            return ((List<?>) ((Map<?, ?>) list.get("items")).get("anyOf")).stream()
                    .map(choice -> String.valueOf(((Map<?, ?>) choice).get("const")))
                    .toList();
        }
    }
}
