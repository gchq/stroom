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
import stroom.ai.impl.tools.timeline.PersonScope;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestCompareActivityTool {

    private static final int CHAT = 7;
    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 5, 4);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 6, 1);
    private static final LocalDate STEP = LocalDate.of(2026, 5, 11);
    private static final String FOLDER = "/outputs/compare/p1/";

    @TempDir
    Path tempDir;

    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final TimelineEventXml eventXml = Mockito.mock(TimelineEventXml.class);
    private final List<QueryField> fields = new ArrayList<>(List.of(QueryField.createDate("@timestamp"),
            QueryField.createKeyword("user.id"), QueryField.createKeyword("user.name"),
            QueryField.createKeyword("host.name"), QueryField.createKeyword("destination.domain"),
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
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenAnswer(invocation ->
                proxy.getName().equals(invocation.getArgument(0)) || proxy.getUuid().equals(invocation.getArgument(0))
                        ? List.of(proxy)
                        : List.of());
        Mockito.when(dataSourceAccess.timeField(proxy)).thenReturn(Optional.of(QueryField.createDate("@timestamp")));
        Mockito.when(queryService.findFields(Mockito.any(FindFieldCriteria.class))).thenAnswer(invocation ->
                new ResultPage<>(fields));
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.any())).thenReturn(Map.of(
                InstructionRules.PERSON_FIELD, new FieldRule(List.of("user.id"), "/Security"),
                InstructionRules.USER_FIELD, new FieldRule(List.of("user.name"), "/Security"),
                InstructionRules.HOST_FIELD, new FieldRule(List.of("host.name"), "/Security")));
        Mockito.when(eventXml.read(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString())).thenReturn(new TimelineEventXml.Read(Map.of(), 0, 0, 0, true));
        tool = new CompareActivityTool(files.workspace(), dataSourceAccess, () -> queryService,
                fixtures.aggregates(), new TimelineFields(agentInstructions, dataSourceAccess, Optional.empty()),
                new PeerGroups(eventXml), auditLog);

        for (int i = 0; i < 10; i++) {
            fixtures.workdays("other" + i, FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of("host.name", "h"));
        }
        for (LocalDate date = FROM; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            fixtures.hours("svc", date, 0, 24, 1, Map.of());
        }
        fixtures.workdays("p1", FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 20, date -> Map.of("host.name", "ws-1"));
    }

    @Test
    void comparesOverMcp() {
        final Map<String, Object> json = json(mcp(), arguments());

        assertThat(json.get("note")).isEqualTo(CompareResult.INDICATORS);
        assertThat(sources(json).getFirst()).containsEntry("index", "Proxy")
                .containsEntry("breadth_fields", List.of("host.name"));
        assertThat(String.valueOf(sources(json).getFirst().get("by")))
                .isEqualTo("by person id (user.id from the AGENTS doc's ## Fields (/Security))");
        final List<Map<String, Object>> findings = list(json, "findings");
        assertThat(findings).isNotEmpty().hasSizeLessThanOrEqualTo(CompareActivityTool.MAX_FINDINGS);
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding).containsEntry("kind", "change").containsEntry("feature", "events");
            assertThat(finding.get("alternative")).isNotNull();
        });
        assertThat(String.valueOf(json.get("months"))).contains("2026-05 Proxy (window)");
        assertThat(json.get("files")).isEqualTo(List.of(FOLDER + "profile.csv", FOLDER + "monthly.csv",
                FOLDER + "findings.csv", FOLDER + "values.csv"));
        assertThat(files.content(FOLDER + "profile.csv").orElseThrow().lines().findFirst().orElseThrow())
                .isEqualTo("date,source,status,events,active_hours,first_activity,last_activity,"
                           + "distinct:host.name,new:host.name");
        assertThat(files.content(FOLDER + "profile.csv").orElseThrow())
                .contains("2026-05-12,Proxy,scored,160,8,09:05,16:52,1,0");
        Mockito.verify(auditLog).searched(Mockito.any(), Mockito.eq(CompareFixtures.PROXY), Mockito.contains(
                "more aggregates"), Mockito.anyLong(), Mockito.contains("by the compare_activity tool"));
    }

    @Test
    void comparesInAChat() {
        final String text = tool.execute(call(arguments()), chat()).text();

        assertThat(text).contains(CompareResult.INDICATORS)
                .contains("**Findings**")
                .contains("Alternatively: ")
                .contains("**By month**")
                .contains("To see what happened on the flagged days: activity_timeline on ");
    }

    @Test
    void aReRunAsksOnlyForTheDaysSince() {
        json(mcp(), arguments());
        final int first = fixtures.aggregates().ran().size();
        final Map<String, Object> later = arguments();
        later.put(CompareActivityTool.WINDOW_TO_ARG, "2026-06-08");
        later.put(CompareActivityTool.BASELINE_FROM_ARG, FROM.toString());
        final Map<String, Object> json = json(mcp(), later);

        final List<AggregateQuery> again = fixtures.aggregates().ran().subList(first,
                fixtures.aggregates().ran().size());
        // The bucketed aggregates start at the last day held (31 May), not 5 January; the rest run whole.
        assertThat(again).filteredOn(query -> query.bucket() != AggregateQuery.Bucket.NONE)
                .isNotEmpty()
                .allSatisfy(query -> assertThat(query.from())
                        .isAfterOrEqualTo(LocalDate.of(2026, 5, 31).atStartOfDay(ZONE).toInstant()))
                .anySatisfy(query -> assertThat(query.from())
                        .isEqualTo(LocalDate.of(2026, 5, 31).atStartOfDay(ZONE).toInstant()));
        assertThat(String.valueOf(json.get("months"))).contains("2026-01 Proxy");
    }

    @Test
    void fallsBackToTheLogonIds() {
        fields.removeIf(field -> field.getFldName().equals("user.id"));
        fixtures.aggregates().source(CompareFixtures.PROXY.getUuid()).forEach(event ->
                event.put("user.name", event.get("user.id")));
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.LOGON_IDS_ARG, List.of("p1"));
        final String text = tool.execute(call(arguments), chat()).text();

        assertThat(text).contains("by logon id (user.name from the AGENTS doc's ## Fields (/Security))")
                .contains(PersonScope.OTHER_ACCOUNTS);
    }

    @Test
    void anIndexWithNoPersonFieldAndNoLogonIdsIsLeftOut() {
        fields.removeIf(field -> field.getFldName().equals("user.id"));
        final ToolResult result = tool.execute(call(arguments()), mcp());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("No source can be compared: Proxy: no field holding the person id");
    }

    @Test
    void refusals() {
        assertThat(error(Map.of())).contains("Give person (the person id) or logonIds");
        final Map<String, Object> nationality = arguments();
        nationality.put(CompareActivityTool.PEER_ELEMENT_ARG, "Nationality");
        assertThat(error(nationality)).contains("never by a personal characteristic such as Nationality");
        final Map<String, Object> overlapping = arguments();
        overlapping.put(CompareActivityTool.BASELINE_TO_ARG, "2026-05-20");
        assertThat(error(overlapping)).contains("The baseline must end by the window's start");
        final Map<String, Object> unit = arguments();
        unit.put(CompareActivityTool.UNIT_ARG, "fortnight");
        assertThat(error(unit)).contains("unit must be day, week or month");
        final Map<String, Object> leave = arguments();
        leave.put(CompareActivityTool.LEAVE_ARG, List.of("next week"));
        assertThat(error(leave)).contains("Leave 'next week' can't be read");
        final Map<String, Object> unknown = arguments();
        unknown.put(CompareActivityTool.SOURCES_ARG, List.of("Nope"));
        assertThat(error(unknown)).contains("No index you can use is called 'Nope'");
    }

    @Test
    void peersFromTheirOrganisationInTheEventXml() {
        final List<String> peers = IntStream.range(0, 6).mapToObj(i -> "peer" + i).toList();
        peers.forEach(peer -> fixtures.workdays(peer, FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of()));
        Mockito.when(eventXml.read(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString())).thenAnswer(invocation -> read(invocation.getArgument(1)));
        final Map<String, Object> arguments = arguments();
        arguments.put(CompareActivityTool.PEERS_ARG, true);
        final Map<String, Object> json = json(mcp(), arguments);

        assertThat(list(json, "findings")).anySatisfy(finding -> assertThat(finding)
                .containsEntry("kind", "peers"));
        assertThat(String.valueOf(json.get("months"))).contains("peers' median");
        assertThat(files.content(PeerGroups.CACHE).orElseThrow()).contains("\"p1\" : \"Finance\"")
                .contains("\"other0\" : \"Sales\"");
        // A second call reads no XML: the groups are kept.
        json(mcp(), arguments);
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void peersWithoutViewData() {
        Mockito.when(eventXml.read(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString())).thenReturn(new TimelineEventXml.Read(Map.of(), 0, 0, 12, false));
        final Map<String, Object> json = json(mcp(), arguments());

        assertThat(String.valueOf(json.get("notes"))).contains("You may not view data");
        assertThat(list(json, "findings")).noneSatisfy(finding -> assertThat(finding)
                .containsEntry("kind", "peers"));
    }

    private TimelineEventXml.Read read(final Map<Long, List<Long>> ids) {
        final Map<String, String> xml = new LinkedHashMap<>();
        ids.forEach((stream, events) -> events.forEach(event -> xml.put(stream + ":" + event,
                eventXml(organisationOf(stream, event)))));
        return new TimelineEventXml.Read(xml, 0, 0, 0, true);
    }

    // The people's organisations: p1 and the peers in Finance, the others in Sales, by the event's person.
    private String organisationOf(final long stream, final long event) {
        final Object who = fixtures.aggregates().source(CompareFixtures.PROXY.getUuid()).stream()
                .filter(e -> e.get("StreamId").equals(stream) && e.get("EventId").equals(event))
                .map(e -> e.get("user.id"))
                .findFirst()
                .orElse("");
        final String person = String.valueOf(who);
        return person.equals("p1") || person.startsWith("peer")
                ? "Finance"
                : "Sales";
    }

    private static String eventXml(final String organisation) {
        return "<?xml version=\"1.1\" encoding=\"UTF-8\"?><Events xmlns=\"event-logging:3\"><Event><EventSource>"
               + "<User><Id>x</Id><UserDetails><Organisation>" + organisation + "</Organisation>"
               + "<Nationality>X</Nationality></UserDetails></User></EventSource></Event></Events>";
    }

    private Map<String, Object> arguments() {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(CompareActivityTool.PERSON_ARG, "p1");
        arguments.put(CompareActivityTool.WINDOW_FROM_ARG, WINDOW_FROM.toString());
        arguments.put(CompareActivityTool.WINDOW_TO_ARG, WINDOW_TO.toString());
        arguments.put(CompareActivityTool.BASELINE_FROM_ARG, FROM.toString());
        arguments.put("timeZone", ZONE.getId());
        return arguments;
    }

    private String error(final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(call(arguments), mcp());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(final ToolCallContext context, final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(call(arguments), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return JsonUtil.readValue(result.text(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Map<String, Object> json, final String key) {
        return (List<Map<String, Object>>) json.get(key);
    }

    private static List<Map<String, Object>> sources(final Map<String, Object> json) {
        return list(json, "sources");
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
