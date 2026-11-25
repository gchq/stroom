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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.streams.StreamProvenance;
import stroom.ai.impl.tools.timeline.TimelineEventXml;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// compare_activity's population screen (`scope: population`) over events held in memory: everyone scored from
/// aggregates with the person a group of each, ranked; `where`, `rare` (the threshold, new values, CDN noise, the
/// fields offered without it), per-person exceptions, re-baselining and peers.
@SuppressWarnings("unchecked")
class TestCompareActivityScreen {

    private static final int CHAT = 7;
    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 5, 4);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 6, 1);
    private static final LocalDate STEP = LocalDate.of(2026, 5, 11);
    private static final List<LocalDate> NIGHTS = List.of(LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 20),
            LocalDate.of(2026, 5, 27));
    private static final String DOMAIN = "destination.domain";

    @TempDir
    Path tempDir;

    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final TimelineEventXml eventXml = Mockito.mock(TimelineEventXml.class);
    private final List<QueryField> fields = new ArrayList<>(List.of(QueryField.createDate("@timestamp"),
            QueryField.createKeyword("user.id"), QueryField.createKeyword("host.name"),
            QueryField.createKeyword(DOMAIN), QueryField.createKeyword("process.name"),
            QueryField.createId("StreamId"), QueryField.createId("EventId")));
    private FakeTabWorkspace files;
    private CompareFixtures fixtures;
    private CompareActivityTool tool;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        fixtures = new CompareFixtures(ZONE);
        final DocRef proxy = CompareFixtures.PROXY;
        Mockito.when(dataSourceAccess.listUsable()).thenReturn(List.of(proxy));
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenReturn(List.of(proxy));
        Mockito.when(dataSourceAccess.timeField(proxy)).thenReturn(Optional.of(QueryField.createDate("@timestamp")));
        Mockito.when(queryService.findFields(Mockito.any(FindFieldCriteria.class))).thenAnswer(invocation ->
                new ResultPage<>(fields));
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.any())).thenReturn(Map.of(
                InstructionRules.PERSON_FIELD, new FieldRule(List.of("user.id"), "/Security"),
                InstructionRules.HOST_FIELD, new FieldRule(List.of("host.name"), "/Security")));
        final TimelineFields timelineFields = new TimelineFields(agentInstructions, dataSourceAccess,
                Optional.empty(), () -> Mockito.mock(DataSourceDocumentation.class));
        tool = new CompareActivityTool(files.workspace(), dataSourceAccess, () -> queryService,
                fixtures.aggregates(), timelineFields, new PeerGroups(eventXml), Mockito.mock(AiAuditLog.class),
                Mockito.mock(StreamProvenance.class));

        for (int i = 0; i < 10; i++) {
            fixtures.workdays("other" + i, FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of("host.name", "h",
                    DOMAIN, "intranet.corp.example"));
        }
        // Four times their events from STEP, in the window.
        fixtures.workdays("riser", FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 20, date -> Map.of("host.name", "ws-1", DOMAIN, date.isBefore(STEP)
                ? "intranet.corp.example"
                : "mydrive.personal-cloud.example"));
        // Late at night on three days in the window.
        fixtures.workdays("nightowl", FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of("host.name", "h",
                DOMAIN, "intranet.corp.example"));
        NIGHTS.forEach(night -> fixtures.hours("nightowl", night, 23, 24, 3, Map.of("host.name", "h")));
    }

    @Test
    void everyoneIsScoredFromAggregatesOfEveryoneAndRanked() {
        final Map<String, Object> json = json(mcp(), arguments());

        assertThat(json).containsEntry("scope", "population").containsEntry("population", 12)
                .containsEntry("note", ScreenResult.INDICATORS);
        assertThat(people(json)).containsExactlyInAnyOrder("riser", "nightowl");
        assertThat(json.get("others_scored")).isEqualTo(10);
        assertThat((List<Map<String, Object>>) ranked(json).getFirst().get("findings")).isNotEmpty()
                .allSatisfy(finding -> assertThat(finding).containsKeys("evidence", "alternative"));
        assertThat(ranked(json)).filteredOn(person -> "nightowl".equals(person.get("person")))
                .singleElement().satisfies(person -> assertThat(String.valueOf(person.get("findings")))
                        .contains("Last activity of the day 23:"));
        // Never a query a person: the person field is a group, never a filter.
        assertThat(fixtures.aggregates().ran()).isNotEmpty().allSatisfy(query ->
                assertThat(query.filters()).doesNotContainKey("user.id"));
        assertThat(fixtures.aggregates().ran()).filteredOn(query -> query.bucket() == AggregateQuery.Bucket.DAY
                                                                    && query.groups().equals(List.of("user.id")))
                .isNotEmpty();
        assertThat((List<String>) json.get("files")).contains("/outputs/compare/population/people.csv",
                "/outputs/compare/population/findings.csv");
        assertThat(files.content("/outputs/compare/population/people.csv").orElseThrow())
                .startsWith("rank,person,score,findings,sources,top_finding")
                .contains(",other0,0,0,");
        assertThat(files.content("/outputs/compare/population/findings.csv").orElseThrow().lines().findFirst()
                .orElseThrow()).isEqualTo("person,rank,source,kind,feature,window,baseline,value,score,ranked,"
                                          + "source_wide,evidence,alternative");
        assertThat(String.valueOf(json.get("next_step"))).startsWith("Next: compare_activity with person ");
        assertThat(String.valueOf(json.get("notes"))).contains("Screened 12 people in 1 source(s)")
                .contains("never a query a person")
                .doesNotContain("row limit");
        // No rare field: the fields that could be screened for one (none here has many values).
        assertThat(json).doesNotContainKey("rare");
    }

    @Test
    void inAChatTheScreenIsMarkdown() {
        final ToolResult result = tool.execute(call(arguments()), chat());

        assertThat(result.isError()).as(result.text()).isFalse();
        assertThat(result.text()).contains("(population screen): 12 people")
                .contains(ScreenResult.INDICATORS)
                .contains("**Ranked** (2 of 12 with any finding)")
                .contains("Alternatively: ")
                .contains("10 others scored.")
                .contains("Next: compare_activity with person ");
    }

    @Test
    void whereLimitsTheEventsScreened() {
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.WHERE_ARG, List.of(Map.of("field", "host.name", "value", "ws-1")));

        final Map<String, Object> json = json(mcp(), arguments);

        assertThat(json.get("population")).isEqualTo(1);
        assertThat(json.get("where")).isEqualTo(List.of("host.name = ws-1"));
        // Everyone's volume a day stays unfiltered (coverage); the rest are filtered.
        assertThat(fixtures.aggregates().ran()).filteredOn(query -> query.groups().contains("user.id"))
                .isNotEmpty()
                .allSatisfy(query -> assertThat(query.filters()).containsEntry("host.name", List.of("ws-1")));
        assertThat((List<String>) json.get("files")).first().asString().startsWith(
                "/outputs/compare/population/host.name_ws-1/");

        final Map<String, Object> in = arguments();
        in.put(CompareActivityTool.WHERE_ARG, List.of(Map.of("field", "host.name", "condition", "in", "value",
                "ws-1, h")));
        assertThat(json(mcp(), in).get("population")).isEqualTo(12);
    }

    @Test
    void whereIsCheckedAndASourceWithoutItsFieldLeftOut() {
        final Map<String, Object> missing = arguments();
        missing.put(CompareActivityTool.WHERE_ARG, List.of(Map.of("field", "process.command_line", "value", "x")));
        assertThat(error(missing)).contains("No source can be screened")
                .contains("it has no field process.command_line (where)");

        final Map<String, Object> contains = arguments();
        contains.put(CompareActivityTool.WHERE_ARG, List.of(Map.of("field", "host.name", "condition", "CONTAINS",
                "value", "ws")));
        assertThat(error(contains)).contains("takes EQUALS (an exact value) or IN");

        final Map<String, Object> noValue = arguments();
        noValue.put(CompareActivityTool.WHERE_ARG, List.of(Map.of("field", "host.name")));
        assertThat(error(noValue)).contains("give host.name a value");

        final Map<String, Object> shape = arguments();
        shape.put(CompareActivityTool.WHERE_ARG, "host.name = ws-1");
        assertThat(error(shape)).contains("must be a list of");

        assertThat(CompareActivityTool.conditions(List.of(Map.of("field", "a", "value", "1"),
                Map.of("field", "b", "condition", "IN", "value", "x, y,,x")))).extracting(
                CompareActivityTool.Condition::text).containsExactly("a = 1", "b in (x, y)");
        assertThat(CompareActivityTool.conditions(null)).isEmpty();
        assertThat(CompareActivityTool.conditions("[{\"field\": \"a\", \"value\": \"1\"}]")).hasSize(1);
    }

    @Test
    void aWindowOverAllTheDataLeavesNoBaselineAndIsRefused() {
        // Eval case 111 (Haiku, 2026-11-26): the window was every month the data covers, so everyone was ranked
        // against an empty baseline.
        final Map<String, Object> whole = arguments();
        whole.put(CompareActivityTool.WINDOW_FROM_ARG, FROM.toString());
        whole.remove(CompareActivityTool.BASELINE_FROM_ARG);

        assertThat(error(whole)).contains("The baseline")
                .contains("holds no events in any source")
                .contains("the first is on " + FROM)
                .contains("baselineFrom=" + FROM)
                .contains("Nothing was ranked");
    }

    @Test
    void aScreenIsOfEveryoneAndWhereAndRareAreForAScreen() {
        final Map<String, Object> withPerson = arguments();
        withPerson.put(CompareActivityTool.PERSON_ARG, "riser");
        assertThat(error(withPerson)).contains("screens everyone in the sources: give no person");

        final Map<String, Object> oneWithWhere = arguments();
        oneWithWhere.remove(CompareActivityTool.SCOPE_ARG);
        oneWithWhere.put(CompareActivityTool.PERSON_ARG, "riser");
        oneWithWhere.put(CompareActivityTool.RARE_ARG, DOMAIN);
        assertThat(error(oneWithWhere)).contains("where and rare are for scope: population");

        final Map<String, Object> unknown = arguments();
        unknown.put(CompareActivityTool.SCOPE_ARG, "team");
        assertThat(error(unknown)).contains("scope must be person or population, not 'team'");
    }

    @Test
    void rareValuesAreThoseFewHoldAndNewForThePersonRankedFirst() {
        // Three people hold one domain all along (rare: 3 of 12), four another (not rare).
        for (int i = 0; i < 4; i++) {
            final String domain = i < 3
                    ? "three.partner.example"
                    : "four.partner.example";
            fixtures.workdays("other" + i, FROM, WINDOW_TO, 12, 13, date -> 1, date -> Map.of("host.name", "h",
                    DOMAIN, domain));
        }
        for (int i = 4; i < 7; i++) {
            fixtures.workdays("other" + i, FROM, WINDOW_TO, 12, 13, date -> 1, date -> Map.of("host.name", "h",
                    DOMAIN, "four.partner.example"));
        }
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.RARE_ARG, DOMAIN);

        final Map<String, Object> json = json(mcp(), arguments);

        final Map<String, Object> rare = ((List<Map<String, Object>>) json.get("rare")).getFirst();
        assertThat(rare).containsEntry("field", DOMAIN).containsEntry("threshold", 3).containsEntry("population", 12);
        final List<Map<String, Object>> values = (List<Map<String, Object>>) rare.get("values");
        assertThat(values).extracting(value -> value.get("value"))
                .containsExactly("mydrive.personal-cloud.example", "three.partner.example");
        assertThat(values.getFirst()).containsEntry("people", 1).containsEntry("new_for", List.of("riser"));
        assertThat(values.get(1)).containsEntry("people", 3).containsEntry("new_for", List.of());
        assertThat(ranked(json)).filteredOn(person -> "riser".equals(person.get("person"))).singleElement()
                .satisfies(person -> assertThat(files.content("/outputs/compare/population/findings.csv")
                        .orElseThrow()).contains("riser,").contains(",rare,rare:destination.domain,"));
        assertThat(files.content("/outputs/compare/population/rare.csv").orElseThrow())
                .startsWith("source,field,value,people,events,first,last,holders,new_for")
                .contains("mydrive.personal-cloud.example,1,");
    }

    @Test
    void withoutRareTheFieldsWithManyValuesAreOffered() {
        for (int i = 0; i < 25; i++) {
            final String site = "site" + i + ".example";
            fixtures.hours("other0", WINDOW_FROM.plusDays(1), 10, 11, 1, Map.of("host.name", "h", DOMAIN, site));
        }

        final Map<String, Object> json = json(mcp(), arguments());

        assertThat((List<Map<String, Object>>) json.get("rare_fields")).singleElement().satisfies(field ->
                assertThat(field).containsEntry("field", DOMAIN).containsEntry("index", "Proxy"));
    }

    @Test
    void aServicesRareSubdomainsAreCdnNoiseWithTheFieldThatGroupsThem() {
        fields.add(QueryField.createKeyword("destination.registered_domain"));
        for (int i = 0; i < 6; i++) {
            fixtures.hours("other" + i, WINDOW_FROM.plusDays(2), 10, 11, 1, Map.of("host.name", "h", DOMAIN,
                    "e" + i + "x.cdn-edge.example"));
        }
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.RARE_ARG, DOMAIN);

        final Map<String, Object> json = json(mcp(), arguments);

        final Map<String, Object> rare = ((List<Map<String, Object>>) json.get("rare")).getFirst();
        assertThat(String.valueOf(rare.get("values"))).doesNotContain("cdn-edge")
                .contains("mydrive.personal-cloud.example");
        assertThat(String.valueOf(rare.get("note"))).contains("CDN noise").contains("cdn-edge.example (6)")
                .contains("rare=destination.registered_domain");
    }

    @Test
    void aPersonsOwnExceptionIsntScoredAndOthersArent() {
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.EXCEPTIONS_ARG, List.of("riser 2026-05-04..2026-05-31: an approved "
                                                                  + "migration"));

        final Map<String, Object> json = json(mcp(), arguments);

        assertThat(people(json)).contains("nightowl").doesNotContain("riser");
        assertThat(CompareActivityTool.PersonDates.read(List.of("2026-04-06..2026-04-10", "p1: 2026-05-01"),
                List.of("p2 2026-05-11..2026-05-15: reason")).leaveOf()).containsOnlyKeys("p1");
    }

    @Test
    void aLastingChangeMonthsBeforeTheWindowIsTheirNewNormal() {
        fixtures.workdays("mover", FROM, WINDOW_TO, 9, 17, date -> date.isBefore(LocalDate.of(2026, 2, 16))
                ? 5
                : 15, date -> Map.of("host.name", "h"));

        final Map<String, Object> json = json(mcp(), arguments());

        assertThat(people(json)).doesNotContain("mover");
        assertThat(String.valueOf(json.get("notes"))).contains("re-baselined").contains("mover from 2026-02-");
    }

    @Test
    void peersAreReadOnceForEveryoneAndCompared() {
        Mockito.when(eventXml.read(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString())).thenAnswer(invocation -> {
                    final Map<Long, List<Long>> ids = invocation.getArgument(1);
                    final Map<String, String> xml = new LinkedHashMap<>();
                    ids.forEach((stream, events) -> events.forEach(event -> xml.put(stream + ":" + event,
                            "<Events><Event><EventSource><User><UserDetails><Organisation>Finance</Organisation>"
                            + "</UserDetails></User></EventSource></Event></Events>")));
                    return new TimelineEventXml.Read(xml, 0, 0, 0, true);
                });
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.PEERS_ARG, true);

        final Map<String, Object> json = json(mcp(), arguments);

        // One read of everyone's events (a call), not one a person.
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any(), Mockito.any(), Mockito.anyString());
        assertThat(files.content("/outputs/compare/population/findings.csv").orElseThrow())
                .contains("riser,").contains(",peers,events_vs_peers,");
        assertThat(String.valueOf(json.get("notes"))).contains("and peers");
    }

    @Test
    void whatAScreenDoesntReadIsSaid() {
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.INCLUDE_TARGETS_ARG, true);
        arguments.put(CompareActivityTool.ACCESS_FIELD_ARG, "host.name");

        final String notes = String.valueOf(json(mcp(), arguments).get("notes"));

        assertThat(notes).contains("includeTargets isn't read in a screen")
                .contains("accessField isn't read in a screen");
    }

    private static List<Map<String, Object>> ranked(final Map<String, Object> json) {
        return (List<Map<String, Object>>) json.get("ranked");
    }

    private static List<Object> people(final Map<String, Object> json) {
        return ranked(json).stream().map(person -> person.get("person")).toList();
    }

    private Map<String, Object> arguments() {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(CompareActivityTool.SCOPE_ARG, CompareActivityTool.POPULATION);
        arguments.put(CompareActivityTool.WINDOW_FROM_ARG, WINDOW_FROM.toString());
        arguments.put(CompareActivityTool.WINDOW_TO_ARG, WINDOW_TO.minusDays(1).toString());
        arguments.put(CompareActivityTool.BASELINE_FROM_ARG, FROM.toString());
        arguments.put("timeZone", ZONE.getId());
        return arguments;
    }

    private String error(final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(call(arguments), mcp());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }

    private Map<String, Object> json(final ToolCallContext context, final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(call(arguments), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return JsonUtil.readValue(result.text(), Map.class);
    }

    private static ToolCall call(final Map<String, Object> arguments) {
        return ToolCalls.call(CompareActivityTool.NAME, JsonUtil.writeValueAsString(arguments, false));
    }

    private ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .sideCalls(ModelSideCalls.NONE)
                .build();
    }

    private ToolCallContext chat() {
        return ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .build();
    }
}
