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
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.sequence.InMemoryKeyStreams;
import stroom.ai.impl.tools.skills.SkillCatalogue;
import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.ai.impl.tools.skills.SkillDoc;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
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

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` from any source (part 2c): sources with no roles have their events classified from their
/// Events XML (the user and time always the index's), else the index's element field, else their own event codes
/// classified once; pairs from the schema and inferred from the user's own events; the views and both tables; the
/// sources asked in a form; the user field found by the AGENTS doc's `## Fields`; and what was learned offered for
/// the skill only to a user who may change it.
@SuppressWarnings("unchecked")
class TestActivityTimelineAnySource {

    private static final int CHAT = 1;
    private static final long STREAM = 9;
    private static final DocRef EVENTS = new DocRef(ElasticIndexDoc.TYPE, "events-uuid", "Events");
    private static final DocRef CODES = new DocRef(ElasticIndexDoc.TYPE, "codes-uuid", "Codes");
    private static final DocRef SKILLS = new DocRef(DocumentationDoc.TYPE, "skills-uuid", "SKILLS");
    private static final String FOLDER = "/outputs/timelines/pat/";

    @TempDir
    Path tempDir;

    private final InMemoryKeyStreams streams = new InMemoryKeyStreams();
    private final ElasticEsql elasticEsql = Mockito.mock(ElasticEsql.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final TimelineEventXml eventXml = Mockito.mock(TimelineEventXml.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final SkillCatalogue skillCatalogue = Mockito.mock(SkillCatalogue.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final Map<String, String> xml = new LinkedHashMap<>();
    private FakeTabWorkspace files;
    private ActivityTimelineTool tool;
    private List<Map<String, Object>> events;
    private List<Map<String, Object>> codes;
    private int eventId;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        Mockito.when(files.workspace().readLines(Mockito.anyInt(), Mockito.any(WorkspacePath.class),
                Mockito.anyLong(), Mockito.anyInt())).thenAnswer(invocation -> files.content(
                invocation.<WorkspacePath>getArgument(1).path()).orElse("").lines().toList());
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenAnswer(invocation -> {
            final String given = invocation.getArgument(0);
            return List.of(EVENTS, CODES).stream()
                    .filter(doc -> doc.getUuid().equals(given) || doc.getName().equals(given))
                    .toList();
        });
        Mockito.when(dataSourceAccess.listUsable()).thenReturn(List.of(EVENTS, CODES));
        // The index holds the user as its pipeline normalised it, lower case without the domain; its element and
        // Action besides the XML; no StreamId in the codes index, which wasn't built from Events.
        final Map<String, String> eventsMapping = Map.of("EventTime", "date", "UserId", "keyword", "HostName",
                "keyword", "SchemaAction", "keyword", "event.action", "keyword", "StreamId", "long", "EventId",
                "long");
        final Map<String, String> codesMapping = Map.of("@timestamp", "date", "user", "keyword", "code", "keyword",
                "client", "keyword", "staff", "keyword");
        Mockito.when(queryService.findFields(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            final List<QueryField> fields = new ArrayList<>();
            (EVENTS.equals(doc)
                    ? eventsMapping
                    : codesMapping).forEach((name, type) -> fields.add("date".equals(type)
                    ? QueryField.createDate(name)
                    : "long".equals(type)
                            ? QueryField.createId(name)
                            : QueryField.createKeyword(name)));
            return new ResultPage<>(fields);
        });
        events = streams.add(EVENTS, "EventTime", eventsMapping);
        codes = streams.add(CODES, "@timestamp", codesMapping);
        Mockito.when(eventXml.read(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final Map<Long, List<Long>> ids = invocation.getArgument(1);
                    final Map<String, String> read = new LinkedHashMap<>();
                    int missing = 0;
                    for (final Map.Entry<Long, List<Long>> stream : ids.entrySet()) {
                        for (final long id : stream.getValue()) {
                            final String text = xml.get(stream.getKey() + ":" + id);
                            if (text == null) {
                                missing++;
                            } else {
                                read.put(stream.getKey() + ":" + id, text);
                            }
                        }
                    }
                    return new TimelineEventXml.Read(read, missing, 0, 0, true);
                });
        tool = new ActivityTimelineTool(files.workspace(), () -> streams, dataSourceAccess, () -> queryService,
                Mockito.mock(AiAuditLog.class), eventXml, new TimelineFields(agentInstructions, dataSourceAccess,
                Optional.empty()), new TimelineSourceChoice(dataSourceAccess, () -> Mockito.mock(TabSnapshots.class)),
                () -> skillCatalogue, documentAccess, agentInstructions, () -> elasticEsql,
                () -> Mockito.mock(DataSourceDocumentation.class));
    }

    @Test
    void eventsWithNoRolesAreClassifiedFromTheirXmlTheUserAndTimeTheIndexs() {
        week();

        final Map<String, Object> result = json(mcp(), arguments(Map.of()));

        // Found as the index holds the user; the XML's "CORP\PAT" and its year-2001 times are never read as such.
        assertThat(result).containsEntry("session_count", 0L).containsEntry("unattached_actions", 0L);
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("user_field", "UserId")
                .containsEntry("user_field_from", "the call (or skill)")
                .containsEntry("classified_by", Map.of("Events XML", 11L));
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("read", 11L)
                .containsEntry("past_the_cap", 0L);
        final String spans = files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow();
        assertThat(spans.lines().findFirst().orElseThrow()).isEqualTo(String.join(",",
                TimelineTables.SPAN_COLUMNS));
        // No focus session, but spans of other kinds on the host: counted, as chat 195's "0 session(s) on 0
        // host(s)" didn't.
        assertThat(result).containsEntry("span_count", spans.lines().count() - 1)
                .containsEntry("span_hosts", List.of("ws-09"));
        assertThat(spans).doesNotContain("2001")
                // The web views by their domain, then the mail sent: an activity period each.
                .contains("2026-11-10T09:00:00Z,2026-11-10T09:20:00Z,View · research.example.org,View ×3,ws-09,"
                          + "activity")
                // Active until the lock.
                .contains("2026-11-10T09:20:00Z,2026-11-10T09:40:00Z,Send · Q3 proposal,Send ×1,ws-09,activity")
                // The schema's pairs: the lock to the unlock, the process from its start to its end.
                .contains("2026-11-10T09:40:00Z,2026-11-10T10:00:00Z,locked,Authenticate ScreenLock to ScreenUnlock,"
                          + "ws-09,pair")
                // What happened leads the label (chat 195: a span's label was only its object).
                .contains("2026-11-10T10:10:00Z,2026-11-10T10:30:00Z,Process Execute · notepad.exe,Process Execute to "
                          + "Terminate,ws-09,pair");
        assertThat(files.content(FOLDER + ActivityTimelineTool.EVENTS_FILE).orElseThrow())
                .contains(",Authenticate/ScreenLock,Events XML").doesNotContain("CORP");
        assertThat((List<Map<String, Object>>) result.get("pairs")).extracting(pair -> pair.get("start"),
                pair -> pair.get("origin")).contains(org.assertj.core.groups.Tuple.tuple("Process/Execute",
                "schema"), org.assertj.core.groups.Tuple.tuple("Authenticate/ScreenLock", "schema"));
        // The XML of the user's events alone was read, by stream and event id.
        Mockito.verify(eventXml).read(Mockito.any(), Mockito.eq(Map.of(STREAM, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L,
                8L, 9L, 10L, 11L))), Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void whereTheXmlIsGoneTheIndexsElementFieldClassifiesWithTheActionTheAgentsDocNames() {
        week();
        xml.clear();
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.USER_FIELD, new FieldRule(List.of("Initiator.Id", "UserId"), "/Indexes"),
                InstructionRules.ACTION_FIELD, new FieldRule(List.of("event.action"), "/Indexes")));
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.remove("userField");

        final Map<String, Object> result = json(mcp(), arguments);

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        // The site standard's first field isn't in this index: its second is.
        assertThat(source).containsEntry("user_field", "UserId")
                .containsEntry("user_field_from", "the AGENTS doc's ## Fields (/Indexes)")
                .containsEntry("classified_by", Map.of("index fields", 11L));
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("gone_or_not_events", 11L);
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow())
                .contains("2026-11-10T09:40:00Z,2026-11-10T10:00:00Z,locked,Authenticate ScreenLock to ScreenUnlock,"
                          + "ws-09,pair")
                // An activity period labelled by its events' kind isn't led by it again.
                .contains(",view,View ×3,ws-09,activity");
    }

    @Test
    void anIndexNotBuiltFromEventsHasItsOwnCodesClassifiedOnceAndConfirmed() {
        code("09:00", "pat", "A-101");
        code("09:05", "pat", "A-101");
        code("09:30", "pat", "Z-900");
        code("09:50", "pat", "A-102");
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(CODES))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("code"), "/")));
        final List<String> asked = new ArrayList<>();
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
                asked.add(question.user());
                return new Answer("{\"A-101\": {\"role\": \"action\", \"kind\": \"file read\"}, \"Z-900\": "
                                  + "{\"role\": \"ignore\"}, \"A-102\": {\"role\": \"idle\"}}", 1, 1);
            }
        };
        final Questions questions = new Questions(Map.of("use", ActivityTimelineTool.USE_CODES));
        final Map<String, Object> arguments = arguments(Map.of("sources", List.of(Map.of("index", "Codes", "user",
                "user"))));

        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), chat(questions, model)).text();

        assertThat(asked).singleElement().asString().contains("A-101").contains("Z-900");
        assertThat(questions.intros).singleElement().asString().contains("A-101: action (file read)")
                .contains("Z-900: ignore");
        assertThat(text).contains("events classified 4 by event code");
        // The ignored code's event is left out; the idle code ends the period.
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow())
                .contains("2026-11-10T09:00:00Z,2026-11-10T09:05:00Z,A-101 · file read,A-101 ×2,Codes,activity");
        // Kept for the index, so a re-run asks nothing.
        assertThat(files.content(EventCodes.FOLDER + "codes-uuid.json")).isPresent();
        asked.clear();
        tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(arguments, false)),
                chat(questions, model));
        assertThat(asked).isEmpty();
    }

    @Test
    void aCodeClassifiedAsAFocusMakesFocusSessionsAndIsNeverPartOfAnInferredPair() {
        // Eval case 77 with no roles: a window's focus and its loss alternate by the window, but the focus is the
        // focus sessions', so no pair is inferred from it and the sessions are by the application.
        final String[][] windows = {{"09:00", "09:20", "WINWORD.EXE"}, {"09:30", "09:50", "chrome.exe"},
                {"10:00", "10:20", "WINWORD.EXE"}, {"10:30", "10:50", "EXCEL.EXE"}};
        for (final String[] window : windows) {
            codes.add(new LinkedHashMap<>(Map.of("@timestamp", at(window[0]), "user", "pat", "code", "win-focus",
                    "client", window[2])));
            codes.add(new LinkedHashMap<>(Map.of("@timestamp", at(window[1]), "user", "pat", "code", "win-blur",
                    "client", window[2])));
        }
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(CODES))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("code"), "/")));
        final List<String> instructions = new ArrayList<>();
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
                instructions.add(question.system());
                return new Answer("{\"win-focus\": {\"role\": \"focus\"}, \"win-blur\": {\"role\": \"action\", "
                                  + "\"kind\": \"focus lost\"}}", 1, 1);
            }
        };
        final Questions questions = new Questions(Map.of("use", ActivityTimelineTool.USE_CODES));
        final Map<String, Object> arguments = arguments(Map.of("sources", List.of(Map.of("index", "Codes", "user",
                "user", "process", "client"))));

        tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(arguments, false)),
                chat(questions, model));
        final Map<String, Object> result = json(mcp(), arguments);

        assertThat(instructions).singleElement().asString().contains("\"focus\" (a window or application came to "
                                                                    + "the front");
        assertThat(questions.intros).singleElement().asString().contains("win-focus: focus");
        assertThat(result).containsEntry("session_count", 4L);
        assertThat((List<Map<String, Object>>) result.get("pairs"))
                .noneSatisfy(pair -> assertThat(pair.get("start")).isEqualTo("win-focus"));
        assertThat((List<List<Object>>) result.get("rows")).extracting(row -> row.get(1))
                .contains("WINWORD.EXE", "chrome.exe", "EXCEL.EXE");
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow())
                .contains(",WINWORD.EXE,focus: focus lost ×1,");
    }

    @Test
    void aSourceWritingTheUserWithADomainAndInCapitalsIsReadWithoutThem() {
        code("09:00", "CORP\\PAT", "A-101");
        code("09:05", "CORP\\PAT", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("sources", List.of(Map.of("index", "Codes", "user",
                "user"))));

        final Map<String, Object> result = json(mcp(), arguments);

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("events", 2L).containsEntry("user_transform",
                "strip_domain, lowercase (the user wasn't found as given)");
        // No model over this client: the codes are left as they are, actions of their own kind.
        assertThat(source).containsEntry("classified_by", Map.of("not classified", 2L));
    }

    @Test
    void aVendorPairIsInferredAndOfferedForTheSkillOnlyToAUserWhoMayEditIt() {
        tunnels();
        skill("""
                # User activity timeline

                Builds a timeline.

                ## Steps

                1. Build it.
                """);
        Mockito.when(documentAccess.canEdit(SKILLS)).thenReturn(true);

        final Map<String, Object> result = json(mcp(), arguments(Map.of("skill", "User activity timeline")));

        assertThat((List<Map<String, Object>>) result.get("pairs")).anySatisfy(pair -> assertThat(pair)
                .containsEntry("start", "Tun-Ascend").containsEntry("end", "Tun-Recede")
                .containsEntry("by", "Tunnel/Id").containsEntry("origin", "inferred")
                .containsEntry("alternating_percent", 100L));
        final Map<String, Object> learned = (Map<String, Object>) result.get("learned");
        assertThat((List<String>) learned.get("event_chains")).containsExactly(
                "- Events: pair Tun-Ascend -> Tun-Recede by Tunnel/Id");
        assertThat(learned).containsEntry("can_record", true);
        assertThat((String) learned.get("offer")).contains("propose_skill action=propose doc=skills-uuid draft="
                                                           + FOLDER + TimelineLearning.SKILL_FILE);
        assertThat(files.content(FOLDER + TimelineLearning.SKILL_FILE).orElseThrow())
                .contains("## Event chains\n\n- Events: pair Tun-Ascend -> Tun-Recede by Tunnel/Id");
    }

    @Test
    void aUserWhoMayNotEditTheSkillIsntOfferedToRecordItButToldWhoCould() {
        tunnels();
        skill("# User activity timeline\n\nBuilds a timeline.\n\n## Steps\n\n1. Build it.\n");
        Mockito.when(documentAccess.canEdit(SKILLS)).thenReturn(false);

        final Map<String, Object> result = json(mcp(), arguments(Map.of("skill", "User activity timeline")));

        final Map<String, Object> learned = (Map<String, Object>) result.get("learned");
        assertThat(learned).containsEntry("can_record", false);
        assertThat((String) learned.get("offer")).doesNotContain("propose_skill")
                .contains("kept for this chat").contains("someone with Edit on the skills doc 'SKILLS'");
        assertThat(files.content(FOLDER + TimelineLearning.CHAINS_FILE).orElseThrow())
                .isEqualTo("- Events: pair Tun-Ascend -> Tun-Recede by Tunnel/Id\n");
        assertThat(files.content(FOLDER + TimelineLearning.SKILL_FILE)).isEmpty();
    }

    @Test
    void aPairTheSkillHoldsIsAppliedAndNothingIsLearnedAgain() {
        tunnels();
        skill("# User activity timeline\n\nBuilds a timeline.\n\n## Event chains\n\n- Events: pair Tun-Ascend -> "
              + "Tun-Recede by Tunnel/Id\n");
        Mockito.when(documentAccess.canEdit(SKILLS)).thenReturn(true);

        final Map<String, Object> result = json(mcp(), arguments(Map.of("skill", "User activity timeline")));

        assertThat((List<Map<String, Object>>) result.get("pairs")).anySatisfy(pair -> assertThat(pair)
                .containsEntry("start", "Tun-Ascend").containsEntry("origin", "skill"));
        assertThat(result).doesNotContainKey("learned");
    }

    @Test
    void theActivitiesViewCharacterisesAndBothTablesAreWritten() {
        week();

        final Map<String, Object> result = json(mcp(), arguments(Map.of("view", "both")));

        final Map<String, Object> view = (Map<String, Object>) result.get("view");
        assertThat((List<Map<String, Object>>) view.get("gantt_charts")).extracting(chart -> chart.get("path"),
                chart -> chart.get("label"), chart -> chart.get("lane")).containsExactly(
                org.assertj.core.groups.Tuple.tuple(FOLDER + "activities.csv", "activity_label", "day"),
                org.assertj.core.groups.Tuple.tuple(FOLDER + "spans.csv", "label", "lane"));
        assertThat(result).containsKey("characterisation");
        assertThat(files.content(FOLDER + ActivityTimelineTool.ACTIVITIES_FILE).orElseThrow().lines().findFirst()
                .orElseThrow()).isEqualTo(String.join(",", TimelineTables.ACTIVITY_COLUMNS));

        // The detail view alone: the activities table is still written, empty, and the spans have no activity.
        final Map<String, Object> detail = json(mcp(), arguments(Map.of("lanes", "source")));
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) detail.get("view")).get("gantt_charts"))
                .singleElement().satisfies(chart -> assertThat(chart).containsEntry("lane", "source"));
        assertThat(detail).doesNotContainKey("characterisation");
        assertThat(files.content(FOLDER + ActivityTimelineTool.ACTIVITIES_FILE).orElseThrow().lines()).hasSize(1);
    }

    @Test
    void aViewTooDenseForOneChartIsSaid() {
        for (int i = 0; i < 260; i++) {
            // A different site every 10 minutes: a period each.
            web(Instant.parse("2026-11-10T00:00:00Z").toEpochMilli() + i * 600_000L, "pat",
                    "https://site" + i + ".example.org/");
        }

        final Map<String, Object> result = json(mcp(), arguments(Map.of("timeTo", "2026-11-12T12:00:00Z")));

        assertThat((List<String>) ((Map<String, Object>) result.get("view")).get("too_dense")).singleElement()
                .asString().startsWith("the detail view has 260 bars in 1 lanes, too many for one chart");
    }

    @Test
    void withNoSourcesTheChatAsksAndTheUsersWordsAreMatchedAndConfirmed() {
        week();
        code("09:00", "pat", "A-101");
        final Questions questions = new Questions(Map.of("sources", TimelineSourceChoice.OWN_WORDS, "other",
                "the events index", "indexes", List.of("Events")));
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.remove("sources");

        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), chat(questions, ModelSideCalls.NONE)).text();

        assertThat(questions.forms).extracting(form -> ((Map<String, Object>) form.get("properties")).keySet())
                .containsExactly(java.util.Set.of("sources", "other"), java.util.Set.of("indexes"));
        // Offered: all supported (the default) or their own words; no dashboard is attached.
        assertThat((List<String>) ((Map<String, Object>) ((Map<String, Object>) questions.forms.getFirst()
                .get("properties")).get("sources")).get("enum")).containsExactly(TimelineSourceChoice.ALL,
                TimelineSourceChoice.OWN_WORDS);
        assertThat(text).contains("Sources: the data sources matching the user's words 'the events index', as they "
                                  + "confirmed.").contains("- Events: user field UserId");
    }

    @Test
    void overMcpWithNoSourcesEverySupportedIndexIsReadThoseWithoutTheUserListedAndLeftOut() {
        week();
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(CODES))).thenReturn(Map.of(
                InstructionRules.USER_FIELD, new FieldRule(List.of("user"), "/")));
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.remove("sources");

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat((String) result.get("sources_chosen")).startsWith("all supported data sources (2 Elastic "
                                                                     + "Index(es) you can use)");
        assertThat((List<Map<String, Object>>) result.get("sources")).extracting(source -> source.get("index"))
                .containsExactly("Events");
        assertThat((List<Map<String, Object>>) result.get("left_out")).singleElement().satisfies(left ->
                assertThat(left).containsEntry("index", "Codes").containsEntry("why", "no events of pat in the "
                                                                                      + "period"));
    }

    @Test
    void aPersonIsReadByThePersonFieldWhereAnIndexHasOneElseByEachOfTheLogonIds() {
        week();
        // The same person's admin account, on the index with no person field.
        final Map<String, Object> admin = new LinkedHashMap<>(Map.of("EventTime", at("11:00"), "UserId", "adm-pat",
                "HostName", "srv-01", "SchemaAction", "View", "StreamId", STREAM, "EventId", 500L));
        events.add(admin);
        codes.add(new LinkedHashMap<>(Map.of("@timestamp", at("09:00"), "user", "PAT01", "staff", "P1", "code",
                "A-1")));
        codes.add(new LinkedHashMap<>(Map.of("@timestamp", at("09:10"), "user", "PAT01", "staff", "P1", "code",
                "A-1")));
        // Another person's, never read.
        codes.add(new LinkedHashMap<>(Map.of("@timestamp", at("09:20"), "user", "BOB01", "staff", "P2", "code",
                "A-1")));
        final Map<String, Object> arguments = arguments(Map.of("person", "P1", "personField", "staff", "logonIds",
                List.of("adm-pat"), "sources", List.of("Events", "Codes")));

        final Map<String, Object> result = json(mcp(), arguments);

        final List<Map<String, Object>> sources = (List<Map<String, Object>>) result.get("sources");
        assertThat(sources).extracting(source -> source.get("index"), source -> source.get("by"),
                source -> source.get("user_field"), source -> source.get("events")).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Events", "by logon id", "UserId", 12L),
                org.assertj.core.groups.Tuple.tuple("Codes", "by person id", "staff", 2L));
        // Each account kept visible, and the others said not to be included.
        assertThat(sources.getFirst()).containsEntry("accounts", Map.of("pat", 11L, "adm-pat", 1L));
        assertThat(sources.get(1)).doesNotContainKey("accounts");
        assertThat(result).containsEntry("entity", "P1")
                .containsEntry("other_accounts", PersonScope.OTHER_ACCOUNTS);
    }

    @Test
    void aSourceWhereNoLogonIdMatchesIsLeftOutWithItsCloseVariantsNeverMerged() {
        week();
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(CODES))).thenReturn(Map.of(
                InstructionRules.USER_FIELD, new FieldRule(List.of("user"), "/")));
        code("09:00", "CORP\\pat", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("logonIds", List.of("CORP\\pat", "adm-pat"),
                "sources", List.of("Events", "Codes")));
        arguments.remove("entity");

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat((List<Map<String, Object>>) result.get("sources")).singleElement().satisfies(source ->
                assertThat(source).containsEntry("index", "Codes").containsEntry("accounts",
                        Map.of("CORP\\pat", 1L)));
        // The index holds the user without the domain: offered, not read.
        assertThat((List<Map<String, Object>>) result.get("left_out")).singleElement().satisfies(left ->
                assertThat(left).containsEntry("index", "Events").containsEntry("why", "no events by logon id "
                                                                                       + "(CORP\\pat, adm-pat) in "
                                                                                       + "the period; close variants "
                                                                                       + "in its index: pat (not "
                                                                                       + "merged: give them in "
                                                                                       + "logonIds if they are the "
                                                                                       + "same person's)"));
    }

    @Test
    void aPersonOnAnIndexWithNoPersonFieldAndNoLogonIdsIsLeftOutSayingWhy() {
        week();
        codes.add(new LinkedHashMap<>(Map.of("@timestamp", at("09:00"), "user", "PAT01", "staff", "P1", "code",
                "A-1")));
        final Map<String, Object> arguments = arguments(Map.of("person", "P1", "personField", "staff", "sources",
                List.of("Events", "Codes")));
        arguments.remove("entity");

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat((List<Map<String, Object>>) result.get("sources")).extracting(source -> source.get("index"))
                .containsExactly("Codes");
        assertThat((List<Map<String, Object>>) result.get("left_out")).singleElement().satisfies(left ->
                assertThat((String) left.get("why")).contains("no field holding the person id"));
        assertThat(result).doesNotContainKey("other_accounts");
    }

    @Test
    void aPeriodWithNoneOfTheUsersEventsGivesEachSourcesFirstAndLastAtAnyTime() {
        week();
        final List<ElasticEsql.Request> asked = new ArrayList<>();
        Mockito.when(elasticEsql.query(Mockito.any())).thenAnswer(invocation -> {
            final ElasticEsql.Request request = invocation.getArgument(0);
            asked.add(request);
            return new ElasticEsql.Result("FROM x", "x", List.of(new ElasticEsql.Column("first", "date"),
                    new ElasticEsql.Column("last", "date"), new ElasticEsql.Column("events", "long")),
                    List.of(List.of("2026-11-10T08:55:00.000Z", "2026-11-10T10:30:00.000Z", 11L)), 1, 1, null,
                    false, false, null, null, "9.5.3", List.of());
        });
        final Map<String, Object> arguments = arguments(Map.of("timeFrom", "2026-11-20T00:00:00Z", "timeTo",
                "2026-11-21T00:00:00Z"));

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat(result).containsEntry("events_read", 0L);
        assertThat((List<Map<String, Object>>) result.get("none_in_period")).singleElement().satisfies(range ->
                assertThat(range).containsEntry("source", "Events").containsEntry("events", 11L)
                        .containsEntry("first", "2026-11-10T08:55:00.000Z")
                        .containsEntry("last", "2026-11-10T10:30:00.000Z"));
        // One aggregate on the user's events, at any time.
        assertThat(asked).singleElement().satisfies(request -> {
            // Not "first" or "last", which ES|QL keeps for itself.
            assertThat(request.pipeline()).isEqualTo("| STATS first_seen = MIN(`EventTime`), last_seen = "
                                                     + "MAX(`EventTime`), events = COUNT(*)");
            assertThat(request.timeRange()).isNull();
            assertThat(request.filter().toString()).contains("UserId").contains("pat");
        });
        // Where every source is left out, the refusal says it too.
        final Map<String, Object> two = new LinkedHashMap<>(arguments);
        two.put("sources", List.of("Events", "Codes"));
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(CODES))).thenReturn(Map.of(
                InstructionRules.USER_FIELD, new FieldRule(List.of("user"), "/")));
        assertThat(tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(two, false)),
                mcp()).text()).contains("None in the period. Their events at any time, per source:\n- Events: 11 "
                                        + "event(s), first 2026-11-10T08:55:00.000Z, last "
                                        + "2026-11-10T10:30:00.000Z");
    }

    // ------------------------------------------------------------------------------------------------

    // A morning: a logon, web views, a mail sent, a lock and an unlock, a process run; the XML holds the user as
    // the source wrote it and its own (wrong) times.
    private void week() {
        event("08:55", "Authenticate", "Logon", null, Map.of(), null);
        event("09:00", "View", null, null, Map.of("Resource/URL", "https://research.example.org/pricing"),
                "https://research.example.org/pricing");
        event("09:05", "View", null, null, Map.of("Resource/URL", "https://research.example.org/saas"),
                "https://research.example.org/saas");
        event("09:12", "View", null, null, Map.of("Resource/URL", "https://research.example.org/bench"),
                "https://research.example.org/bench");
        event("09:20", "Send", null, null, Map.of("Email/Subject", "Q3 proposal"), "Q3 proposal");
        event("09:40", "Authenticate", "ScreenLock", null, Map.of(), null);
        event("10:00", "Authenticate", "ScreenUnlock", null, Map.of(), null);
        event("10:05", "View", null, null, Map.of("Resource/URL", "https://news.example.com/"),
                "https://news.example.com/");
        event("10:10", "Process", "Execute", null, Map.of("Command", "C:\\Windows\\notepad.exe", "ProcessId",
                "77"), null);
        event("10:20", "View", null, null, Map.of("Resource/URL", "https://news.example.com/x"),
                "https://news.example.com/x");
        event("10:30", "Process", "Terminate", null, Map.of("Command", "C:\\Windows\\notepad.exe", "ProcessId",
                "77"), null);
        // Another user's event, never read.
        final Map<String, Object> other = new LinkedHashMap<>(Map.of("EventTime", at("09:00"), "UserId", "bob",
                "HostName", "ws-10", "StreamId", STREAM, "EventId", 999L));
        events.add(other);
    }

    // A vendor's tunnel, up and down by its id, with names the schema doesn't know.
    private void tunnels() {
        for (int tunnel = 1; tunnel <= 4; tunnel++) {
            final String hour = "1" + tunnel;
            event(hour + ":00", "Network", null, "Tun-Ascend", Map.of("Tunnel/Id", "t" + tunnel), null);
            event(hour + ":20", "Network", null, "Tun-Recede", Map.of("Tunnel/Id", "t" + tunnel), null);
        }
    }

    private void event(final String time,
                       final String element,
                       final String action,
                       final String typeId,
                       final Map<String, String> leaves,
                       final String object) {
        final long id = ++eventId;
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("EventTime", at(time));
        doc.put("UserId", "pat");
        doc.put("HostName", "ws-09");
        doc.put("SchemaAction", element);
        if (action != null) {
            doc.put("event.action", action);
        }
        doc.put("StreamId", STREAM);
        doc.put("EventId", id);
        events.add(doc);
        final StringBuilder sb = new StringBuilder("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<Events "
                                                   + "xmlns=\"event-logging:3\"><Event><EventTime><TimeCreated>"
                                                   + "2001-01-01T00:00:00.000Z</TimeCreated></EventTime>"
                                                   + "<EventSource><Device><HostName>WS-09.CORP</HostName></Device>"
                                                   + "<User><Id>CORP\\PAT</Id></User></EventSource><EventDetail>");
        if (typeId != null) {
            sb.append("<TypeId>").append(typeId).append("</TypeId>");
        }
        sb.append('<').append(element).append('>');
        if (action != null) {
            sb.append("<Action>").append(action).append("</Action>");
        }
        leaves.forEach((path, value) -> {
            final String[] parts = path.split("/");
            for (final String part : parts) {
                sb.append('<').append(part).append('>');
            }
            sb.append(value.replace("&", "&amp;"));
            for (int i = parts.length - 1; i >= 0; i--) {
                sb.append("</").append(parts[i]).append('>');
            }
        });
        sb.append("</").append(element).append("></EventDetail></Event></Events>");
        xml.put(STREAM + ":" + id, sb.toString());
        if (object != null) {
            assertThat(sb.toString()).contains(object.replace("&", "&amp;"));
        }
    }

    private void code(final String time, final String user, final String value) {
        codes.add(new LinkedHashMap<>(Map.of("@timestamp", at(time), "user", user, "code", value)));
    }

    private void web(final long time, final String user, final String url) {
        final long id = ++eventId;
        events.add(new LinkedHashMap<>(Map.of("EventTime", time, "UserId", user, "HostName", "ws-09",
                "SchemaAction", "View", "StreamId", STREAM, "EventId", id)));
        xml.put(STREAM + ":" + id, "<Event><EventDetail><View><Resource><URL>" + url
                                   + "</URL></Resource></View></EventDetail></Event>");
    }

    private Listed skill(final String markdown) {
        final SkillDoc.Skill skill = SkillDoc.parse(markdown).skills().getFirst();
        final Listed listed = new Listed(SKILLS, ExplorerConstants.SYSTEM_DOC_REF, "/", skill, -1);
        Mockito.when(skillCatalogue.readable(Mockito.any())).thenReturn(List.of(listed));
        return listed;
    }

    private Map<String, Object> arguments(final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("entity", "pat");
        arguments.put("timeFrom", "2026-11-10T00:00:00Z");
        arguments.put("timeTo", "2026-11-10T18:00:00Z");
        arguments.put("sources", List.of(Map.of("index", "Events", "host", "HostName")));
        arguments.put("userField", "UserId");
        arguments.putAll(more);
        return arguments;
    }

    private ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .build();
    }

    private ToolCallContext chat(final UserQuestions questions, final ModelSideCalls sideCalls) {
        return ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(questions)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .sideCalls(sideCalls)
                .build();
    }

    private Map<String, Object> json(final ToolCallContext context, final Map<String, Object> arguments) {
        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), context).text();
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
            return list.stream().map(TestActivityTimelineAnySource::longs).toList();
        }
        return value;
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli();
    }


    // --------------------------------------------------------------------------------


    // A user answering every form from the same answers, each form's own keys.
    private static final class Questions implements UserQuestions {

        private final Map<String, Object> answers;
        private final List<Map<String, Object>> forms = new ArrayList<>();
        private final List<String> intros = new ArrayList<>();

        Questions(final Map<String, Object> answers) {
            this.answers = answers;
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
            forms.add(form);
            intros.add(intro);
            final Map<String, Object> content = new LinkedHashMap<>();
            ((Map<String, Object>) form.get("properties")).keySet().forEach(key -> {
                if (answers.containsKey(key)) {
                    content.put(key, answers.get(key));
                }
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content);
        }
    }
}
