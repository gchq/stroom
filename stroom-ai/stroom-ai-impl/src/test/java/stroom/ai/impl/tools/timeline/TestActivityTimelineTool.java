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

package stroom.ai.impl.tools.timeline;

import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.sequence.InMemoryKeyStreams;
import stroom.ai.impl.tools.skills.SkillCatalogue;
import stroom.ai.impl.tools.workspace.GanttChartTool;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGanttParser;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` through the tool over [InMemoryKeyStreams]: one user's events of three sources read as the
/// streaming join reads a side (a terms condition for the user, a transform where a source writes them with a
/// domain), the sessions, attachments and flags in the result and the tables, the sessions table drawn by
/// gantt_chart as it is, characterisation over MCP with no sampling and in a chat with a stubbed model, the
/// analyst's corrections kept for a re-run, and the refusals.
@SuppressWarnings("unchecked")
class TestActivityTimelineTool {

    private static final int CHAT = 1;
    private static final DocRef ENDPOINT = new DocRef(ElasticIndexDoc.TYPE, "endpoint-uuid", "Endpoint");
    private static final DocRef WINDOWS = new DocRef(ElasticIndexDoc.TYPE, "windows-uuid", "Windows");
    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "lucene-uuid", "Lucene events");
    private static final String FOLDER = "/outputs/timelines/jsmith/";

    @TempDir
    Path tempDir;

    private final InMemoryKeyStreams streams = new InMemoryKeyStreams();
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final TimelineEventXml eventXml = Mockito.mock(TimelineEventXml.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final SkillCatalogue skillCatalogue = Mockito.mock(SkillCatalogue.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private FakeTabWorkspace files;
    private ActivityTimelineTool tool;
    private List<Map<String, Object>> endpoint;
    private List<Map<String, Object>> windows;
    private List<Map<String, Object>> proxy;
    private int eventId;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        Mockito.when(files.workspace().readLines(Mockito.anyInt(), Mockito.any(WorkspacePath.class),
                Mockito.anyLong(), Mockito.anyInt())).thenAnswer(invocation -> files.content(
                invocation.<WorkspacePath>getArgument(1).path()).orElse("").lines().toList());
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenAnswer(invocation -> {
            final String given = invocation.getArgument(0);
            return List.of(ENDPOINT, WINDOWS, PROXY, LUCENE).stream()
                    .filter(doc -> doc.getUuid().equals(given) || doc.getName().equals(given))
                    .toList();
        });
        final Map<String, String> endpointMapping = Map.ofEntries(Map.entry("EventTime", "date"),
                Map.entry("UserName", "keyword"), Map.entry("Computer", "keyword"), Map.entry("Process", "keyword"),
                Map.entry("WindowTitle", "keyword"), Map.entry("WindowId", "keyword"), Map.entry("Action", "keyword"),
                Map.entry("Target", "keyword"), Map.entry("Text", "keyword"), Map.entry("StreamId", "long"),
                Map.entry("EventId", "long"));
        final Map<String, String> windowsMapping = Map.of("@timestamp", "date", "TargetUserName", "keyword",
                "Computer", "keyword", "EventID", "keyword");
        final Map<String, String> proxyMapping = Map.of("@timestamp", "date", "user", "keyword", "client_host",
                "keyword", "url", "keyword");
        Mockito.when(queryService.findFields(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            final Map<String, String> mapping = ENDPOINT.equals(doc)
                    ? endpointMapping
                    : WINDOWS.equals(doc)
                            ? windowsMapping
                            : proxyMapping;
            final List<QueryField> fields = new ArrayList<>();
            mapping.forEach((name, type) -> fields.add("date".equals(type)
                    ? QueryField.createDate(name)
                    : "long".equals(type)
                            ? QueryField.createId(name)
                            : QueryField.createKeyword(name)));
            return new ResultPage<>(fields);
        });
        endpoint = streams.add(ENDPOINT, "EventTime", endpointMapping);
        windows = streams.add(WINDOWS, "@timestamp", windowsMapping);
        proxy = streams.add(PROXY, "@timestamp", proxyMapping);
        tool = new ActivityTimelineTool(files.workspace(), () -> streams, dataSourceAccess, () -> queryService,
                auditLog, eventXml, new TimelineFields(agentInstructions, dataSourceAccess, Optional.empty()),
                new TimelineSourceChoice(dataSourceAccess, () -> tabSnapshots), () -> skillCatalogue, documentAccess,
                agentInstructions, () -> Mockito.mock(ElasticEsql.class));

        windows("08:30", "4800");
        windows("08:58", "4801");
        endpoint("jsmith", "08:59", "open", "WINWORD.EXE", null, "w1", null);
        endpoint("jsmith", "09:00", "focus", "WINWORD.EXE", "Q3 Proposal.docx - Word", null, null);
        endpoint("jsmith", "09:05", "clipboard", "WINWORD.EXE", null, null, null);
        endpoint("bob", "09:10", "focus", "EXCEL.EXE", "Budget.xlsx - Excel", null, null);
        endpoint("jsmith", "09:20", "focus", "chrome.exe", "Pricing - Google Chrome", null, null);
        proxy("09:25", "ws-01", "https://research.example.org/pricing");
        proxy("09:27", null, "https://research.example.org/pricing/saas");
        endpoint("jsmith", "09:40", "focus", "WINWORD.EXE", "Q3 Proposal.docx - Word", null, null);
        endpoint("jsmith", "09:50", "copy", "explorer.exe", null, null, "E:\\Q3 Proposal.docx");
        endpoint("jsmith", "10:00", "close", "WINWORD.EXE", null, "w1", null);
        // Locked at 10:05; the unlock was dropped, so the next focus comes while the host is locked.
        windows("10:05", "4800");
        endpoint("jsmith", "11:00", "focus", "chrome.exe", "News - Google Chrome", null, null);
        windows("11:10", "4800");
    }

    @Test
    void aUsersSessionsActionsAndFlagsComeFromEachSourceReadForThatUserAlone() {
        final Map<String, Object> result = json(mcp(), arguments(Map.of()));

        assertThat(result).containsEntry("entity", "jsmith").containsEntry("session_count", 4L)
                .containsEntry("hosts", List.of("ws-01")).containsEntry("unattached_actions", 0L)
                .containsEntry("actions", 4L).containsEntry("events_read", 14L);
        assertThat((List<Object>) result.get("rows")).containsExactly(
                List.of("ws-01", "WINWORD.EXE", 2L, 45.0),
                List.of("ws-01", "chrome.exe", 2L, 30.0));
        final Map<String, Object> quality = (Map<String, Object>) result.get("data_quality");
        assertThat(quality).containsOnlyKeys("idle_start_without_end", "focus_without_open");
        assertThat((Map<String, Object>) quality.get("idle_start_without_end")).containsEntry("count", 1L);
        // Each role of each source was a side read for jsmith alone; Windows's users compared without the domain.
        assertThat(streams.scanned()).hasSize(8).allSatisfy(side -> assertThat(side.terms())
                .isEqualTo(List.of(List.of("jsmith"))));
        assertThat(streams.scanned().stream().filter(side -> side.index().equals(WINDOWS)))
                .allSatisfy(side -> assertThat(side.keys().getFirst().transforms())
                        .containsExactly(Transform.STRIP_DOMAIN));
        assertThat(streams.open()).isZero();
        Mockito.verify(auditLog, Mockito.times(3)).searched(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyLong(), Mockito.contains("Activity timeline"));

        final List<String> sessions = files.content(FOLDER + ActivityTimelineTool.SESSIONS_FILE).orElseThrow()
                .lines().toList();
        assertThat(sessions.getFirst()).isEqualTo("session,host,process,start,end,active_minutes,titles,actions,"
                                                  + "start_mark,end_mark,ended_by,activity,activity_label");
        assertThat(sessions).hasSize(5);
        assertThat(sessions.get(1)).startsWith("s1,ws-01,WINWORD.EXE,2026-11-10T09:00:00Z,2026-11-10T09:20:00Z,"
                                               + "20.0,Q3 Proposal.docx - Word,clipboard copy: 1");
        final String actions = files.content(FOLDER + ActivityTimelineTool.ACTIONS_FILE).orElseThrow();
        assertThat(actions).contains("2026-11-10T09:05:00Z,ws-01,WINWORD.EXE,clipboard copy,,s1,process,Endpoint")
                .contains("2026-11-10T09:27:00Z,,,web access,https://research.example.org/pricing/saas,s2,time,"
                          + "Proxy")
                .contains("2026-11-10T09:50:00Z,ws-01,explorer.exe,file copy,E:\\Q3 Proposal.docx,s3,time");
        assertThat(files.content(FOLDER + ActivityTimelineTool.EVENTS_FILE).orElseThrow()).contains(",7,");
        assertThat(files.content(FOLDER + ActivityTimelineTool.WINDOWS_FILE).orElseThrow())
                .contains("ws-01,WINWORD.EXE,w1,2026-11-10T08:59:00Z,2026-11-10T10:00:00Z");
        assertThat(files.content(FOLDER + ActivityTimelineTool.QUALITY_FILE).orElseThrow())
                .contains("idle_start_without_end,1,");
        assertThat((Map<String, Object>) result.get("gantt")).containsEntry("path",
                FOLDER + ActivityTimelineTool.SESSIONS_FILE).containsEntry("lane", "host");
    }

    @Test
    void theSessionsTableIsDrawnByGanttChartAsItIs() {
        json(mcp(), arguments(Map.of()));

        final String chart = new GanttChartTool(files.workspace()).execute(ToolCalls.call(GanttChartTool.NAME,
                        JsonUtil.writeValueAsString(Map.of("path", FOLDER + ActivityTimelineTool.SESSIONS_FILE,
                                "start", "start", "end", "end", "label", "process", "lane", "host",
                                "mergeSeconds", 0, "minSeconds", 0, "timeZone", "UTC"), false)),
                ToolCalls.inChat(CHAT, files.workspace())).text();

        final String block = chart.substring(chart.indexOf("```mermaid\n") + 11, chart.indexOf("```\n\n"));
        final MermaidGantt gantt = MermaidGanttParser.parse(block);
        assertThat(gantt.taskCount()).isEqualTo(4);
        assertThat(block).contains("section ws-01", "WINWORD.EXE :2026-11-10 09:00:00, 2026-11-10 09:20:00",
                "chrome.exe :2026-11-10 11:00:00, 2026-11-10 11:10:00");
    }

    @Test
    void aChatsResultSaysWhatWasFoundAndHowToDrawIt() {
        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments(Map.of("timeZone", "UTC")), false)), ToolCalls.inChat(CHAT, files.workspace())).text();

        assertThat(text).startsWith("Activity timeline of jsmith, 2026-11-10 00:00 to 2026-11-10 12:00: 4 session(s) "
                                    + "on 1 host(s), 4 action(s) (0 in no session or span), from 14 event(s): "
                                    + "Endpoint 8, Windows 4, Proxy 2.")
                .contains("| ws-01 | WINWORD.EXE | 2 | 45.0 |")
                .contains("- idle_start_without_end: 1 idle starts")
                .contains("draw it in the chat with gantt_chart: path " + FOLDER + "spans.csv, start start, end "
                          + "end, label label, lane lane.")
                .contains(ActivityTimelineTool.CAVEAT);
    }

    @Test
    void aUserSeenOnlyInProxyEventsGetsActivityPeriodsNotNothing() {
        // Chat 190: a user seen only in web proxy logs got 0 sessions (sessions began only at a focus event), every
        // action in no session, and nothing to chart. Their actions close in time now make activity periods.
        proxyOf("pat", "09:00", "ws-09", "https://research.example.org/pricing");
        proxyOf("pat", "09:05", "ws-09", "https://research.example.org/pricing/saas");
        proxyOf("pat", "09:12", "ws-09", "https://research.example.org/benchmarks");
        proxyOf("pat", "09:20", "ws-09", "https://news.example.com/local");
        proxyOf("pat", "11:00", "ws-09", "https://research.example.org/pricing");
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.put("entity", "pat");
        arguments.put("sources", List.of(Map.of("index", "Proxy", "user", "user", "host", "client_host", "object",
                "url", "roles", Map.of("actions", List.of(Map.of("kind", "web access", "where", List.of(Map.of(
                        "field", "url", "condition", "IS_NOT_NULL"))))))));

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat(result).containsEntry("session_count", 0L).containsEntry("actions", 5L);
        assertThat((Map<String, Object>) result.get("spans")).containsEntry("activity", 3L);
        final String spans = files.content("/outputs/timelines/pat/spans.csv").orElseThrow();
        assertThat(spans).contains("2026-11-10T09:00:00Z,2026-11-10T09:20:00Z,research.example.org,ws-09,activity")
                .contains("2026-11-10T09:20:00Z,2026-11-10T09:20:00Z,news.example.com,ws-09,activity")
                .contains("2026-11-10T11:00:00Z,2026-11-10T11:00:00Z,research.example.org,ws-09,activity");
    }

    @Test
    void anotherIdleCapEndsTheSessionsSooner() {
        endpoint("jsmith", "11:01", "focus", "notepad.exe", "notes.txt - Notepad", null, null);
        // Nothing after the focus at 11:01 but the lock at 11:10: a 5-minute cap ends notepad at 11:06.
        final Map<String, Object> result = json(mcp(), arguments(Map.of("idleCap", "5m")));

        assertThat(result).containsEntry("idle_cap_minutes", 5L);
        assertThat(files.content(FOLDER + ActivityTimelineTool.SESSIONS_FILE).orElseThrow())
                .contains("notepad.exe,2026-11-10T11:01:00Z,2026-11-10T11:06:00Z");
    }

    @Test
    void characterisedOverMcpWithNoSamplingOnlyTheStrongLinksAreMadeSayingSo() {
        final Map<String, Object> result = json(mcp(), arguments(Map.of("characterise", true)));

        final Map<String, Object> characterisation = (Map<String, Object>) result.get("characterisation");
        assertThat(characterisation).containsEntry("model_calls", 0L);
        final List<Map<String, Object>> activities = (List<Map<String, Object>>) characterisation.get("activities");
        assertThat(activities).isNotEmpty();
        assertThat(activities.getFirst()).containsEntry("label_is", "inferred")
                .containsEntry("found_by", "the sessions' graph (Leiden clusters)");
        assertThat((List<String>) characterisation.get("notes")).anyMatch(note -> note.startsWith(
                "Strong links only") && note.contains("no model"));
        assertThat(files.content(FOLDER + ActivityTimelineTool.ACTIVITIES_FILE)).isPresent();
    }

    @Test
    void characterisedInAChatTheModelIsCalledOutsideTheConversationAndCorrectionsAreKeptForAReRun() {
        endpoint("jsmith", "11:05", "message", "Teams.exe", null, null, null);
        endpoint.getLast().put("Text", "lunch at one?");
        final AtomicInteger calls = new AtomicInteger();
        final ModelSideCalls model = new ModelSideCalls() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String via() {
                return "a stub";
            }

            @Override
            public Answer ask(final Question question) {
                calls.incrementAndGet();
                if (question.system().equals(ActivityCharacterisation.NAMING)) {
                    return new Answer("{\"a1\": \"Q3 proposal\"}", 1, 1);
                }
                final String items = question.user().substring(question.user().indexOf("Items:\n") + 7);
                final List<Map<String, Object>> answers = new ArrayList<>();
                for (final Object item : JsonUtil.readValue(items, List.class)) {
                    answers.add(Map.of("item", ((Map<?, ?>) item).get("item"), "activity", "not_work",
                            "confidence", "high", "reason", "personal"));
                }
                return new Answer(JsonUtil.writeValueAsString(answers, false), 1, 1);
            }
        };
        final Map<String, Object> withText = new LinkedHashMap<>(arguments(Map.of("characterise", true,
                "corrections", List.of(Map.of("rename", "a1", "label", "The Q3 proposal")))));
        ((List<Map<String, Object>>) withText.get("sources")).set(0, endpointSource(true));

        final String first = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                withText, false)), chat(model)).text();

        assertThat(first).contains("\"The Q3 proposal\"").contains("classified by the model in 1 call(s) outside "
                                                                    + "this conversation");
        assertThat(first).doesNotContain("lunch at one?");
        assertThat(calls.get()).isGreaterThanOrEqualTo(1);
        assertThat(files.content(FOLDER + ActivityTimelineTool.CORRECTIONS_FILE).orElseThrow())
                .contains("The Q3 proposal");
        assertThat(files.content(FOLDER + ActivityTimelineTool.ITEMS_FILE).orElseThrow()).contains("not_work");

        // A re-run with no corrections keeps them, and asks the model nothing new.
        calls.set(0);
        withText.remove("corrections");
        final String second = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                withText, false)), chat(model)).text();
        assertThat(second).contains("\"The Q3 proposal\"");
        assertThat(calls.get()).isZero();
    }

    @Test
    void aSourceThatIsntAnElasticIndexOrNamesAFieldOrRoleItHasntIsRefused() {
        final Map<String, Object> lucene = new LinkedHashMap<>(endpointSource(false));
        lucene.put("index", LUCENE.getName());
        assertThat(text(mcp(), Map.of("entity", "jsmith", "sources", List.of(lucene))))
                .isEqualTo("Error: Source 1: Index 'Lucene events' (uuid lucene-uuid) isn't an Elastic Index: a "
                           + "timeline reads Elastic Indexes only.");

        final Map<String, Object> badField = new LinkedHashMap<>(endpointSource(false));
        badField.put("host", "Hostname");
        assertThat(text(mcp(), Map.of("entity", "jsmith", "sources", List.of(badField))))
                .isEqualTo("Error: Source 1: Elastic Index 'Endpoint' has no field 'Hostname'. describe_data_source "
                           + "gives its fields.");

        final Map<String, Object> badRole = new LinkedHashMap<>(endpointSource(false));
        badRole.put("roles", Map.of("hover", Map.of("where", List.of(Map.of("field", "Action", "value", "x")))));
        assertThat(text(mcp(), Map.of("entity", "jsmith", "sources", List.of(badRole))))
                .startsWith("Error: Source 1: 'hover' isn't a role: give focus, open, close, idle_start, idle_end "
                            + "or actions.");

        assertThat(text(mcp(), Map.of("sources", List.of(endpointSource(false)))))
                .startsWith("Error: Give entity");
        assertThat(text(mcp(), Map.of("entity", "jsmith", "sources", List.of(endpointSource(false)),
                "timeFrom", "2026-11-10T12:00:00Z", "timeTo", "2026-11-10T00:00:00Z")))
                .isEqualTo("Error: timeTo must be after timeFrom.");
    }

    // ------------------------------------------------------------------------------------------------

    private Map<String, Object> arguments(final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("entity", "jsmith");
        arguments.put("timeFrom", "2026-11-10T00:00:00Z");
        arguments.put("timeTo", "2026-11-10T12:00:00Z");
        arguments.put("sources", new ArrayList<>(List.of(endpointSource(false), Map.of(
                "index", "Windows", "user", "TargetUserName", "transform", List.of("strip_domain"),
                "host", "Computer", "roles", Map.of(
                        "idle_start", Map.of("where", List.of(Map.of("field", "EventID", "value", "4800"))),
                        "idle_end", Map.of("where", List.of(Map.of("field", "EventID", "value", "4801"))))),
                Map.of("index", "Proxy", "user", "user", "host", "client_host", "object", "url", "roles", Map.of(
                        "actions", List.of(Map.of("kind", "web access", "where", List.of(Map.of(
                                "field", "url", "condition", "IS_NOT_NULL")))))))));
        arguments.putAll(more);
        return arguments;
    }

    private static Map<String, Object> endpointSource(final boolean withText) {
        final Map<String, Object> source = new LinkedHashMap<>();
        source.put("index", "Endpoint");
        source.put("user", "UserName");
        source.put("host", "Computer");
        source.put("process", "Process");
        source.put("title", "WindowTitle");
        source.put("window", "WindowId");
        source.put("object", "Target");
        if (withText) {
            source.put("text", "Text");
        }
        final List<Map<String, Object>> actions = new ArrayList<>(List.of(
                Map.of("kind", "clipboard copy", "where", List.of(Map.of("field", "Action", "value", "clipboard"))),
                Map.of("kind", "file copy", "where", List.of(Map.of("field", "Action", "value", "copy")))));
        if (withText) {
            actions.add(Map.of("kind", "message", "where", List.of(Map.of("field", "Action", "value", "message"))));
        }
        source.put("roles", Map.of(
                "focus", Map.of("where", List.of(Map.of("field", "Action", "value", "focus"))),
                "open", Map.of("where", List.of(Map.of("field", "Action", "value", "open"))),
                "close", Map.of("where", List.of(Map.of("field", "Action", "value", "close"))),
                "actions", actions));
        return source;
    }

    private void endpoint(final String user, final String time, final String action, final String process,
                          final String title, final String window, final String target) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("EventTime", at(time));
        event.put("UserName", user);
        event.put("Computer", "ws-01");
        event.put("Action", action);
        event.put("Process", process);
        if (title != null) {
            event.put("WindowTitle", title);
        }
        if (window != null) {
            event.put("WindowId", window);
        }
        if (target != null) {
            event.put("Target", target);
        }
        event.put("StreamId", 7L);
        event.put("EventId", (long) ++eventId);
        endpoint.add(event);
    }

    private void windows(final String time, final String eventIdValue) {
        windows.add(new LinkedHashMap<>(Map.of("@timestamp", at(time), "TargetUserName", "CORP\\jsmith",
                "Computer", "ws-01", "EventID", eventIdValue)));
    }

    private void proxy(final String time, final String host, final String url) {
        final Map<String, Object> event = new LinkedHashMap<>(Map.of("@timestamp", at(time), "user", "jsmith",
                "url", url));
        if (host != null) {
            event.put("client_host", host);
        }
        proxy.add(event);
    }

    private void proxyOf(final String user, final String time, final String host, final String url) {
        proxy.add(new LinkedHashMap<>(Map.of("@timestamp", at(time), "user", user, "client_host", host, "url",
                url)));
    }

    private ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .build();
    }

    private ToolCallContext chat(final ModelSideCalls sideCalls) {
        return ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .sideCalls(sideCalls)
                .build();
    }

    private String text(final ToolCallContext context, final Map<String, Object> arguments) {
        return tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(arguments, false)),
                context).text();
    }

    private Map<String, Object> json(final ToolCallContext context, final Map<String, Object> arguments) {
        final String text = text(context, arguments);
        assertThat(text).as(text).startsWith("{");
        return (Map<String, Object>) longs(JsonUtil.readValue(text, Map.class));
    }

    // The value with every whole number as a Long, as JSON reads small ones as Integers.
    private static Object longs(final Object value) {
        if (value instanceof final Integer number) {
            return number.longValue();
        } else if (value instanceof final Map<?, ?> map) {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, longs(item)));
            return copy;
        } else if (value instanceof final List<?> list) {
            return list.stream().map(TestActivityTimelineTool::longs).toList();
        }
        return value;
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli();
    }
}
