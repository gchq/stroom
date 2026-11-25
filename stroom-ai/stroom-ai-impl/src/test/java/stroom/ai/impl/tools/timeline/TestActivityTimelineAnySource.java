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
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.catalogue.TimelineRole;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.instructions.InstructionRules.SourcesRule;
import stroom.ai.impl.tools.instructions.InstructionRules.TypeRoleRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.sequence.InMemoryKeyStreams;
import stroom.ai.impl.tools.skills.SkillCatalogue;
import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.ai.impl.tools.skills.SkillDoc;
import stroom.ai.impl.tools.streams.IndexingTrace;
import stroom.ai.impl.tools.streams.StreamProvenance;
import stroom.ai.impl.tools.streams.StreamProvenance.PipelineFacts;
import stroom.ai.impl.tools.streams.StreamProvenance.Provenance;
import stroom.ai.impl.tools.streams.StreamProvenance.StreamSource;
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
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private static final String EVENTS_HINT = "If logons are written with a domain or as an email in 'Events', and "
                                              + "the user says so, give logonForms [strip_domain] (slower: a script "
                                              + "runs over every event in the period), or the exact forms in "
                                              + "logonIds";

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
    private final DataSourceDocumentation plans = Mockito.mock(DataSourceDocumentation.class);
    private final Map<String, String> xml = new LinkedHashMap<>();
    private final StreamProvenance streamProvenance = Mockito.mock(StreamProvenance.class);
    private final IndexingTrace indexingTrace = Mockito.mock(IndexingTrace.class);
    private FakeTabWorkspace files;
    private ActivityTimelineTool tool;
    private List<Map<String, Object>> events;
    private List<Map<String, Object>> codes;
    private int eventId;
    // True to index each event's element and Action besides its XML (the index then holds its type, so its XML isn't
    // read to classify it).
    private boolean elementInIndex;

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
                "long", "TypeId", "keyword", "host.name", "keyword", "url", "keyword");
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
                Optional.empty(), () -> plans),
                new TimelineSourceChoice(dataSourceAccess, () -> Mockito.mock(TabSnapshots.class)),
                () -> skillCatalogue, documentAccess, agentInstructions, () -> elasticEsql,
                () -> plans, () -> streamProvenance, () -> indexingTrace);
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
    void theSchemaTakesASourceAsAnIndexsNameOrAnObject() {
        final ToolSchema sources = tool.definition().parameters().properties().get("sources");

        assertThat(sources).isInstanceOfSatisfying(ToolSchema.ArraySchema.class, array -> assertThat(array.items())
                .isInstanceOfSatisfying(ToolSchema.AnyOfSchema.class, anyOf -> {
                    assertThat(anyOf.anyOf().getFirst()).isInstanceOf(ToolSchema.StringSchema.class);
                    assertThat(anyOf.anyOf().get(1)).isInstanceOfSatisfying(ToolSchema.ObjectSchema.class,
                            object -> assertThat(object.properties()).containsKeys("index", "user", "roles"));
                }));
    }

    @Test
    void aUserFieldFromTheIndexPlanOfAFeedingPipelineIsSaidSoWithNothingToOffer() {
        week();
        final DocRef pipeline = new DocRef("Pipeline", "pipe-uuid", "Events indexing");
        Mockito.when(plans.feedingPlans(EVENTS)).thenReturn(new DataSourceDocumentation.FeedingPlans(
                List.of(pipeline), Map.of(pipeline, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH, "events",
                        "@timestamp", List.of(PlannedField.of("UserId", "keyword", "EventSource/User/Id")))))));
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.remove("userField");

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat(((List<Map<String, Object>>) result.get("sources")).getFirst())
                .containsEntry("user_field", "UserId")
                .containsEntry("user_field_from", "the index plan of 'Events indexing'");
        assertThat(result).doesNotContainKey("fields_lines_to_offer");
    }

    @Test
    void aUserFieldFoundByTheIndexsDocumentationComesWithTheFieldsLineToOffer() {
        week();
        Mockito.when(dataSourceAccess.documentation(EVENTS)).thenReturn(Optional.of("The user is in `UserId`."));
        Mockito.when(agentInstructions.folderOf(EVENTS)).thenReturn("/Indexes");
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.remove("userField");

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat(((List<Map<String, Object>>) result.get("sources")).getFirst())
                .containsEntry("user_field_from", "the index's documentation");
        assertThat(result).containsEntry("fields_lines_to_offer", List.of("To make UserId the user field here, add "
                                                                          + "`- user: UserId` under `## Fields` in "
                                                                          + "an AGENTS doc in /Indexes (with "
                                                                          + "propose_instructions)."));
    }

    @Test
    void whereTheXmlIsGoneTheIndexsElementFieldClassifiesWithTheActionTheAgentsDocNames() {
        elementInIndex = true;
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
        // The index holds each event's type, so no XML is read for it (decided 2026-11-25: XML only where unmapped).
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("with_ids", 0L)
                .containsEntry("read", 0L).doesNotContainKey("paths_read");
        Mockito.verifyNoInteractions(eventXml);
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
    void aSourceWritingTheUserWithADomainIsNotSearchedForItUnasked() {
        code("09:00", "CORP\\PAT", "A-101");
        code("09:05", "CORP\\PAT", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("sources", List.of(Map.of("index", "Codes", "user",
                "user"))));

        final Map<String, Object> result = json(mcp(), arguments);

        // Exact by default: nothing as given, a hint, and no script over the period's events.
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("events", 0L).doesNotContainKey("user_transform");
        assertThat((String) source.get("hint")).contains("logonForms [strip_domain]", "slower");
        assertThat(scripted()).isEmpty();
    }

    @Test
    void logonFormsMatchTheUserWithADomainAndInCapitalsScripted() {
        code("09:00", "CORP\\PAT", "A-101");
        code("09:05", "CORP\\PAT", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("logonForms", List.of("strip_domain", "lowercase"),
                "sources", List.of(Map.of("index", "Codes", "user", "user"))));

        final Map<String, Object> result = json(mcp(), arguments);

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("events", 2L)
                .containsEntry("user_transform", "matched with strip_domain, lowercase: scripted, slower")
                .doesNotContainKey("hint");
        // No model over this client: the codes are left as they are, actions of their own kind.
        assertThat(source).containsEntry("classified_by", Map.of("not classified", 2L));
        assertThat(scripted()).isNotEmpty();
        // Only the transforms of logon forms.
        assertThat(tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(arguments(
                Map.of("logonForms", List.of("file_name"), "sources", List.of(Map.of("index", "Codes", "user",
                        "user")))), false)), mcp()).text())
                .isEqualTo("Error: logonForms takes strip_domain, lowercase: 'file_name' isn't one.");
    }

    @Test
    void aLoneUserFoundAsGivenIsReadOnlyAsGivenWithNoScript() {
        // OTHERDOM\pat may be someone else, and a script over every event is slow: the user is matched exactly.
        code("09:00", "pat", "A-101");
        code("09:05", "pat", "A-101");
        code("09:10", "OTHERDOM\\pat", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("sources", List.of(Map.of("index", "Codes", "user",
                "user"))));

        final Map<String, Object> result = json(mcp(), arguments);

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("events", 2L).doesNotContainKey("user_transform")
                .doesNotContainKey("hint");
        assertThat(scripted()).isEmpty();
    }

    @Test
    void anotherWrittenFormGivenInLogonIdsIsRead() {
        code("09:00", "pat", "A-101");
        code("09:05", "pat", "A-101");
        code("09:10", "OTHERDOM\\pat", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("logonIds", List.of("OTHERDOM\\pat"), "sources",
                List.of(Map.of("index", "Codes", "user", "user"))));

        final Map<String, Object> result = json(mcp(), arguments);

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("events", 3L)
                .containsEntry("accounts", Map.of("pat", 2L, "OTHERDOM\\pat", 1L));
        // Still the timeline of the entity given, its other form one of its accounts.
        assertThat(result).containsEntry("entity", "pat");
        assertThat(scripted()).isEmpty();
    }

    @Test
    void aSourceWithNothingAsGivenHintsAtLogonFormsInTheChat() {
        week();
        code("09:00", "CORP\\pat", "A-101");
        final Map<String, Object> arguments = arguments(Map.of("sources", List.of(Map.of("index", "Events", "host",
                "HostName"), Map.of("index", "Codes", "user", "user"))));

        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), chat(new Questions(Map.of()), ModelSideCalls.NONE)).text();

        assertThat(text).contains("- left out: Codes (no events of pat in the period. If logons are written with a "
                                  + "domain or as an email in 'Codes', and the user says so, give logonForms "
                                  + "[strip_domain]");
        assertThat(scripted()).isEmpty();
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
    void aSourceThatCantBeReadIsLeftOutWithItsReasonAndTheOthersAreRead() {
        // Chat 195: one cluster without its credentials failed the whole timeline.
        week();
        streams.refuse(CODES, "missing authentication credentials for REST request [/]");
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.put("sources", List.of(Map.of("index", "Events", "host", "HostName"), Map.of("index", "Codes",
                "user", "user")));

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat((List<Map<String, Object>>) result.get("sources")).extracting(source -> source.get("index"))
                .containsExactly("Events");
        assertThat((List<Map<String, Object>>) result.get("left_out")).singleElement().satisfies(left ->
                assertThat(left).containsEntry("index", "Codes").containsEntry("why", "it can't be read: The "
                                                                                      + "cluster refused to give its "
                                                                                      + "version: missing "
                                                                                      + "authentication credentials "
                                                                                      + "for REST request [/]"));
        assertThat((Long) result.get("events_read")).isPositive();
    }

    @Test
    void whenNoSourceCanBeReadTheCallFailsSayingWhyForEach() {
        week();
        streams.refuse(EVENTS, "missing authentication credentials");
        streams.refuse(CODES, "connection refused");
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.put("sources", List.of(Map.of("index", "Events", "host", "HostName"), Map.of("index", "Codes",
                "user", "user")));

        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), mcp()).text();

        assertThat(text).startsWith("Error: No source could be read:")
                .contains("- Events: it can't be read: The cluster refused to give its version: missing "
                          + "authentication credentials")
                .contains("- Codes: it can't be read: The cluster refused to give its version: connection refused");
        // One source alone fails as it did.
        arguments.put("sources", List.of(Map.of("index", "Codes", "user", "user")));
        assertThat(tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(arguments,
                false)), mcp()).text()).isEqualTo("Error: Source 1: The cluster refused to give its version: "
                                                  + "connection refused.");
    }

    @Test
    void overMcpWithNoSourcesAnAgentsDocsDefaultSourcesAreRead() {
        week();
        Mockito.when(agentInstructions.sourcesRuled(Mockito.any(), Mockito.eq(InstructionRules.TIMELINE_SOURCES)))
                .thenReturn(Optional.of(new SourcesRule(List.of("Events", "No such index"), "/")));
        final Map<String, Object> arguments = arguments(Map.of());
        arguments.remove("sources");

        final Map<String, Object> result = json(mcp(), arguments);

        assertThat((String) result.get("sources_chosen")).isEqualTo("the AGENTS doc's ## Sources (/); not one "
                                                                     + "Elastic Index you can use: No such index");
        assertThat((List<Map<String, Object>>) result.get("sources")).extracting(source -> source.get("index"))
                .containsExactly("Events");
        // Codes, which the doc doesn't name, isn't even probed.
        assertThat(streams.scanned()).allSatisfy(side -> assertThat(side.index()).isEqualTo(EVENTS));
    }

    @Test
    void overMcpPastTenIndexesHoldingTheUsersEventsTheCallingModelChooses() {
        final List<DocRef> many = new ArrayList<>();
        final Map<String, String> mapping = Map.of("@timestamp", "date", "user", "keyword");
        for (int i = 0; i < ActivityTimelineTool.MAX_AUTOMATIC_SOURCES + 1; i++) {
            final DocRef doc = new DocRef(ElasticIndexDoc.TYPE, "many-" + i, "Many " + i);
            many.add(doc);
            streams.add(doc, "@timestamp", mapping).add(new LinkedHashMap<>(Map.of("@timestamp", at("09:00"),
                    "user", "pat")));
        }
        Mockito.when(dataSourceAccess.listUsable()).thenReturn(many);
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenAnswer(invocation -> many.stream()
                .filter(doc -> doc.getUuid().equals(invocation.getArgument(0)))
                .toList());
        final Map<String, Object> arguments = arguments(Map.of("userField", "user"));
        arguments.remove("sources");

        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), mcp()).text();

        assertThat(text).contains("11 Elastic Indexes hold pat's events in the period, more than the 10 a timeline "
                                  + "reads unasked").contains("- Many 0 (many-0): ").contains("user field user");
        // Probed, never read.
        assertThat(streams.scanned()).hasSize(ActivityTimelineTool.MAX_AUTOMATIC_SOURCES + 1);
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
    void aSourceWhereNoLogonIdMatchesIsLeftOutWithAHintAndNoSearchForOtherForms() {
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
        // The index holds the user without the domain: not searched for, a hint how to ask.
        assertThat((List<Map<String, Object>>) result.get("left_out")).singleElement().satisfies(left ->
                assertThat(left).containsEntry("index", "Events").containsEntry("why", "no events by logon id "
                                                                                       + "(CORP\\pat, adm-pat) in "
                                                                                       + "the period. "
                                                                                       + EVENTS_HINT));
        assertThat(scripted()).isEmpty();
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
    void aPeriodWithMoreEventsThanATimelineReadsIsGivenADayAtATimeAndNoneIsRead() {
        final List<ElasticEsql.Request> asked = new ArrayList<>();
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("TypeId"), "/")));
        typed("09:00", 9, "vpn-up", null);
        typed("09:10", 9, "vpn-up", null);
        typed("09:20", 9, "vpn-up", null);
        Mockito.when(elasticEsql.query(Mockito.any())).thenAnswer(invocation -> {
            final ElasticEsql.Request request = invocation.getArgument(0);
            asked.add(request);
            final List<List<Object>> rows = request.pipeline().contains("first_seen")
                    ? List.of(List.of("2026-09-01T08:00:00.000Z", "2026-11-09T17:00:00.000Z", 2_000_000L))
                    : request.pipeline().contains("BY day, ")
                            ? List.of(List.of(1_200_000L, "2026-10-02", "doc-read"), List.of(800_000L, "2026-10-03",
                            "vpn-up"))
                            : List.of(List.of(1_200_000L, "2026-10-02"), List.of(800_000L, "2026-10-03"));
            return new ElasticEsql.Result("FROM x", "x", List.of(), rows, 1, 1, null, false, true, null, null,
                    "9.5.3", List.of());
        });

        final Map<String, Object> result = json(mcp(), arguments(Map.of("timeFrom", "2026-09-01T00:00:00Z")));

        assertThat(result).containsEntry("overview", true).containsEntry("active_days", 2L);
        assertThat((String) result.get("why")).startsWith("2000000 events in the period, more than the");
        assertThat((List<Map<String, Object>>) result.get("days")).extracting(day -> day.get("day"))
                .containsExactly("2026-10-02", "2026-10-03");
        assertThat((String) result.get("next")).startsWith("Pick the days to read in detail");
        // No raw read: only the probe of the first events (which finds the element field), nor their XML.
        assertThat(streams.scanned()).hasSize(1);
        assertThat(result).doesNotContainKey("events_read");
        Mockito.verifyNoInteractions(eventXml);
        // The count, the days, and the days by the index's type field, in the user's zone.
        assertThat(asked).hasSize(3);
        assertThat(asked.get(2).pipeline()).contains("BY day, `TypeId`");
        assertThat(asked.get(2).timeZone()).isEqualTo("Z");
        assertThat(files.content(FOLDER + ActivityTimelineTool.OVERVIEW_FILE).orElseThrow())
                .contains("2026-10-02,Events,doc-read,1200000");
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
        // The period's events counted first (decided 2026-11-25), then one aggregate on the user's events at any time.
        assertThat(asked).hasSize(2);
        assertThat(asked.getFirst().timeRange()).isNotNull();
        assertThat(asked.getLast()).satisfies(request -> {
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
    // ------------------------------------------------------------------------------------------------
    // Reading (decided 2026-11-25): roles by type, each event's own pipeline's fields, XML only where unmapped

    @Test
    void aTypeTheCatalogueOfItsStreamsTranslationGivesARoleNeedsNoClassifyingAndNoXml() {
        typed("09:00", 9, "vpn-up", null);
        typed("09:10", 9, "doc-read", "https://intranet.example.org/a");
        typed("09:20", 9, "doc-read", "https://intranet.example.org/b");
        typed("09:30", 9, "noise", null);
        typed("09:40", 9, "vpn-down", null);
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("TypeId"), "/"),
                InstructionRules.OBJECT_FIELD, new FieldRule(List.of("url"), "/")));
        final DocRef translation = new DocRef("Pipeline", "vpn-uuid", "VPN translation");
        final PipelineFacts facts = new PipelineFacts(translation, true, List.of(), null,
                new DocRef(DocumentationDoc.TYPE, "notes-uuid", "VPN source notes"), null);
        Mockito.when(streamProvenance.of(Mockito.any())).thenReturn(new Provenance(Map.of(9L, new StreamSource(9L,
                "VPN-EU", "Events", translation, null, 8L)), 0, Map.of(translation.getUuid(), facts)));
        Mockito.when(streamProvenance.timelineRoles(facts)).thenReturn(Map.of(
                "vpn-up", new TimelineRoles.Role("vpn-up", TimelineRole.ACTIVE, "authenticate", "given"),
                "vpn-down", new TimelineRoles.Role("vpn-down", TimelineRole.IDLE, "authenticate", "given"),
                "doc-read", new TimelineRoles.Role("doc-read", TimelineRole.ACTION, "view", "schema"),
                "noise", new TimelineRoles.Role("noise", TimelineRole.IGNORE, "event", "given")));

        // Over MCP with no model: no codes left to classify, so no refusal.
        final Map<String, Object> result = json(mcp(), arguments(Map.of()));

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        // The type read from the index's event type field; its role the catalogue's.
        assertThat(source).containsEntry("classified_by", Map.of("event type field", 5L));
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys(TypeRoleLookup.FROM_CATALOGUE);
        assertThat(result).containsEntry("events_ignored", 1L).doesNotContainKey("event_types_lines_to_offer");
        // The object mapped for the index labels the period, which the catalogue's idle type ends.
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow())
                .contains("2026-11-10T09:10:00Z,2026-11-10T09:40:00Z,doc-read · intranet.example.org,doc-read ×2,");
        // Its kind the catalogue's.
        assertThat(files.content(FOLDER + ActivityTimelineTool.ACTIONS_FILE).orElseThrow()).contains(",view,");
        // One batched lookup of the streams read; their roles read once for the translation; no XML.
        Mockito.verify(streamProvenance, Mockito.times(1)).of(Set.of(9L));
        Mockito.verify(streamProvenance, Mockito.times(1)).timelineRoles(facts);
        Mockito.verifyNoInteractions(eventXml);
    }

    @Test
    void anEventCodeNothingSettlesHasItsElementAndActionReadFromTheXmlBeforeAnyModelIsAsked() {
        typed("09:00", 9, "proc-start", null);
        xml.put("9:" + eventId, "<Event><EventDetail><TypeId>proc-start</TypeId><Process><Action>Execute</Action>"
                                + "<Command>C:\\Windows\\notepad.exe</Command><ProcessId>42</ProcessId></Process>"
                                + "</EventDetail></Event>");
        typed("09:30", 9, "proc-end", null);
        xml.put("9:" + eventId, "<Event><EventDetail><TypeId>proc-end</TypeId><Process><Action>Terminate</Action>"
                                + "<Command>C:\\Windows\\notepad.exe</Command><ProcessId>42</ProcessId></Process>"
                                + "</EventDetail></Event>");
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("TypeId"), "/")));

        // Over MCP with no model: the XML settles the codes, so no refusal.
        final Map<String, Object> result = json(mcp(), arguments(Map.of()));

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("classified_by", Map.of("Events XML", 2L));
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys(TypeRoleLookup.FROM_SCHEMA);
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) result.get("events_xml")).get("paths_read"))
                .singleElement().satisfies(paths -> assertThat(paths).containsEntry("paths",
                        ActivityTimelineTool.TYPE_PATHS).containsEntry("events", 2L));
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow())
                .contains(",proc-start · notepad.exe,proc-start to proc-end,");
    }

    @Test
    void anUnsettledCodeIsClassifiedOnceFromAFewOfItsEventsReadFirstAndTheRestReadForKeysWithinTheCap() {
        for (int i = 0; i < 10; i++) {
            typed("09:" + (10 + 4 * i), 9, "proc-start", null);
            xml.put("9:" + eventId, "<Event><EventDetail><TypeId>proc-start</TypeId><Process><Action>Execute"
                                    + "</Action><Command>C:\\Windows\\notepad.exe</Command><ProcessId>" + i
                                    + "</ProcessId></Process></EventDetail></Event>");
            typed("09:" + (12 + 4 * i), 9, "proc-end", null);
            xml.put("9:" + eventId, "<Event><EventDetail><TypeId>proc-end</TypeId><Process><Action>Terminate"
                                    + "</Action><Command>C:\\Windows\\notepad.exe</Command><ProcessId>" + i
                                    + "</ProcessId></Process></EventDetail></Event>");
        }
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("TypeId"), "/")));

        // An XML cap of a few events per code: each code's few read first, classified once, applied to all.
        final int perCode = ActivityTimelineTool.XML_EVENTS_PER_CODE;
        final Map<String, Object> result = json(mcpWithXmlCap(2 * perCode), arguments(Map.of()));

        final ArgumentCaptor<Map<Long, List<Long>>> ids = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), ids.capture(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
        assertThat(ids.getValue().values().stream().mapToInt(List::size).sum()).isEqualTo(2 * perCode);
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("classified_by", Map.of("Events XML", 2L * perCode,
                "Events XML of its code", 20L - 2L * perCode));
        // Every code classified within the cap: none past it.
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("read", 2L * perCode)
                .containsEntry("past_the_cap", 0L);
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys(TypeRoleLookup.FROM_SCHEMA);
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow())
                .contains("proc-start to proc-end");

        // Within a larger cap the rest of the codes' events are read too, for the keys only their XML holds (each
        // process's id): every process paired.
        final Map<String, Object> whole = json(mcp(), arguments(Map.of()));
        assertThat(((List<Map<String, Object>>) whole.get("sources")).getFirst()).containsEntry("classified_by",
                Map.of("Events XML", 20L));
        assertThat(files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow().lines()
                .filter(line -> line.contains("proc-start to proc-end")).count()).isEqualTo(10);
    }

    private ToolCallContext mcpWithXmlCap(final int cap) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(() -> new AiWorkspaceConfig(null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, cap)))
                .build();
    }

    @Test
    void byDefaultASettledCodesEventsXmlIsNotReadAndTheResultSaysTheMode() {
        settledCodeOfMixedTypes(10);

        final Map<String, Object> result = json(mcp(), arguments(Map.of()));

        // The AGENTS doc settles the code: no Events XML read, every event classified by its code.
        Mockito.verify(eventXml, Mockito.never()).read(Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("classified_by", Map.of("event type field", 10L));
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("mode",
                        ActivityTimelineTool.EVENTS_XML_AS_NEEDED).containsEntry("read", 0L)
                .containsEntry("past_the_cap", 0L);
    }

    @Test
    void eventsXmlEachReadsEveryEventsXmlAndClassifiesEachFromItsOwn() {
        settledCodeOfMixedTypes(10);

        final Map<String, Object> result = json(mcp(), arguments(Map.of(ActivityTimelineTool.EVENTS_XML_ARG,
                ActivityTimelineTool.EVENTS_XML_EACH)));

        final ArgumentCaptor<Map<Long, List<Long>>> ids = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), ids.capture(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
        assertThat(ids.getValue().values().stream().mapToInt(List::size).sum()).isEqualTo(10);
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("classified_by", Map.of("Events XML", 10L));
        // The AGENTS doc's role still applies to the code.
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys(TypeRoleLookup.FROM_AGENTS);
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("mode",
                        ActivityTimelineTool.EVENTS_XML_EACH).containsEntry("read", 10L)
                .containsEntry("past_the_cap", 0L).doesNotContainKey("narrow_the_period");
        // Each event its own type, from its own XML: the code's two event types both seen.
        assertThat(files.content(FOLDER + ActivityTimelineTool.EVENTS_FILE).orElseThrow())
                .contains(",action,process execute,ws-09,notepad.exe,,,9,1,audit,Events XML")
                .contains(",action,view,ws-09,,,doc1.txt,9,2,audit,Events XML");
    }

    @Test
    void eventsXmlEachReadsWithinTheCapAndSaysWhereTheCapCutIt() {
        settledCodeOfMixedTypes(10);

        final Map<String, Object> result = json(mcpWithXmlCap(4), arguments(Map.of(
                ActivityTimelineTool.EVENTS_XML_ARG, ActivityTimelineTool.EVENTS_XML_EACH)));

        final ArgumentCaptor<Map<Long, List<Long>>> ids = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), ids.capture(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
        assertThat(ids.getValue().values().stream().mapToInt(List::size).sum()).isEqualTo(4);
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        // The rest classified by their code, from its events read.
        assertThat(source).containsEntry("classified_by", Map.of("Events XML", 4L, "Events XML of its code", 6L));
        final Map<String, Object> xmlRead = (Map<String, Object>) result.get("events_xml");
        assertThat(xmlRead).containsEntry("mode", ActivityTimelineTool.EVENTS_XML_EACH).containsEntry("read", 4L)
                .containsEntry("with_ids", 10L).containsEntry("past_the_cap", 6L);
        assertThat((String) xmlRead.get("narrow_the_period")).contains("More than 4 events",
                ActivityTimelineTool.EVENTS_XML_ARG);
    }

    @Test
    void anInvalidEventsXmlIsRefused() {
        settledCodeOfMixedTypes(2);

        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments(Map.of(ActivityTimelineTool.EVENTS_XML_ARG, "all")), false)), mcp()).text();

        assertThat(text).startsWith("Error: ").contains(ActivityTimelineTool.EVENTS_XML_ARG, "as_needed", "each");
        Mockito.verify(eventXml, Mockito.never()).read(Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
    }

    @Test
    void theSchemaOffersEventsXmlsModes() {
        assertThat(tool.definition().parameters().properties().get(ActivityTimelineTool.EVENTS_XML_ARG))
                .isInstanceOfSatisfying(ToolSchema.EnumSchema.class, values -> assertThat(values.values())
                        .containsExactly(ActivityTimelineTool.EVENTS_XML_AS_NEEDED,
                                ActivityTimelineTool.EVENTS_XML_EACH));
    }

    // Events of one code an AGENTS doc settles, whose XML holds two event types (alternately a process started and
    // a document viewed): the code doesn't settle an event's type.
    private void settledCodeOfMixedTypes(final int count) {
        for (int i = 0; i < count; i++) {
            typed("09:" + (10 + 2 * i), 9, "audit", null);
            xml.put("9:" + eventId, i % 2 == 0
                    ? "<Event><EventDetail><TypeId>audit</TypeId><Process><Action>Execute</Action><Command>"
                      + "C:\\Windows\\notepad.exe</Command></Process></EventDetail></Event>"
                    : "<Event><EventDetail><TypeId>audit</TypeId><View><Document><Title>doc" + i
                      + ".txt</Title></Document></View></EventDetail></Event>");
        }
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("TypeId"), "/")));
        Mockito.when(agentInstructions.eventTypesRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                "audit", new TypeRoleRule(TimelineRole.ACTION, "/Indexes")));
    }

    @Test
    void anAgentsDocsEventTypesGiveRolesAndTheModelsClassificationIsOfferedAsLines() {
        typed("09:00", 9, "Tun-Ascend", null);
        typed("09:10", 9, "file-open", null);
        typed("09:30", 9, "Tun-Recede", null);
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("TypeId"), "/")));
        Mockito.when(agentInstructions.eventTypesRuled(Mockito.any(), Mockito.eq(EVENTS))).thenReturn(Map.of(
                "tun-ascend", new TypeRoleRule(TimelineRole.ACTIVE, "/Indexes"),
                "Tun-Recede", new TypeRoleRule(TimelineRole.IDLE, "/Indexes")));
        final List<String> asked = new ArrayList<>();
        final ModelSideCalls model = model(asked, "{\"file-open\": {\"role\": \"action\", \"kind\": "
                                                  + "\"file open\"}}");

        final Map<String, Object> result = json(mcpWith(model), arguments(Map.of()));

        // Only the code no AGENTS line gives was asked of the model, once.
        assertThat(asked).singleElement().asString().contains("file-open").doesNotContain("Tun-Ascend");
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys(TypeRoleLookup.FROM_AGENTS,
                TypeRoleLookup.FROM_CODE);
        final Map<String, Object> offer = (Map<String, Object>) result.get("event_types_lines_to_offer");
        assertThat((Map<String, Object>) offer.get("by_index")).containsEntry("Events",
                "## Event types\n\n- file-open: action\n");
        assertThat((String) offer.get("how")).contains("propose_instructions");
    }

    @Test
    void whereThePipelinesFeedingAnIndexDifferEachEventsFieldIsItsOwnPipelines() {
        final DocRef oldIndexing = new DocRef("Pipeline", "old-uuid", "Endpoint indexing V1");
        final DocRef newIndexing = new DocRef("Pipeline", "new-uuid", "Endpoint indexing V2");
        Mockito.when(plans.feedingPlans(EVENTS)).thenReturn(new DataSourceDocumentation.FeedingPlans(
                List.of(oldIndexing, newIndexing), Map.of(
                oldIndexing, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH, "events", "EventTime", List.of(
                        PlannedField.of("HostName", "keyword", "EventSource/Device/HostName")))),
                newIndexing, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH, "events", "EventTime", List.of(
                        PlannedField.of("host.name", "keyword", "EventSource/Device/HostName")))))));
        Mockito.when(streamProvenance.of(Mockito.any())).thenReturn(new Provenance(Map.of(
                21L, new StreamSource(21L, "ENDPOINT-A", "Events", oldIndexing, null, null),
                22L, new StreamSource(22L, "ENDPOINT-B", "Events", newIndexing, null, null)), 0, Map.of(
                oldIndexing.getUuid(), new PipelineFacts(oldIndexing, true, List.of(), null, null, null),
                newIndexing.getUuid(), new PipelineFacts(newIndexing, true, List.of(), null, null, null))));
        Mockito.when(documentAccess.canView(Mockito.any())).thenReturn(true);
        hosted("09:00", 21, Map.of("HostName", "ws-a"));
        // The new version's document holds HostName too, meaning something else there.
        hosted("09:05", 22, Map.of("HostName", "relay-01", "host.name", "ws-b"));
        // A stream not looked up: traced to its indexing pipeline by its tasks, once.
        hosted("09:10", 23, Map.of("HostName", "relay-02", "host.name", "ws-c"));
        hosted("09:12", 23, Map.of("HostName", "relay-02", "host.name", "ws-c"));
        Mockito.when(indexingTrace.trace(Mockito.any(), Mockito.any())).thenReturn(new IndexingTrace.Traced(
                Map.of(23L, newIndexing), Map.of(), Set.of()));
        elementInIndex = true;

        final Map<String, Object> result = json(mcp(), arguments(Map.of("sources", List.of("Events"))));

        assertThat((List<String>) result.get("span_hosts")).containsExactlyInAnyOrder("ws-a", "ws-b", "ws-c");
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat((Map<String, Object>) source.get("fields_by_pipeline")).containsEntry("host", Map.of(
                "HostName (the stream's pipeline)", 1L, "host.name (the stream's pipeline)", 1L,
                "host.name (traced by its tasks)", 2L));
        // Only the stream no producing pipeline settles is traced, once, among the index's feeding pipelines.
        Mockito.verify(indexingTrace, Mockito.times(1)).trace(Set.of(23L), List.of(oldIndexing, newIndexing));
        Mockito.verifyNoInteractions(eventXml);
    }

    @Test
    void whereNoPipelineIsKnownAnEventsDifferingThingIsReadFromItsXmlAtItsPathElseNotRead() {
        final DocRef oldIndexing = new DocRef("Pipeline", "old-uuid", "Endpoint indexing V1");
        final DocRef newIndexing = new DocRef("Pipeline", "new-uuid", "Endpoint indexing V2");
        Mockito.when(plans.feedingPlans(EVENTS)).thenReturn(new DataSourceDocumentation.FeedingPlans(
                List.of(oldIndexing, newIndexing), Map.of(
                oldIndexing, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH, "events", "EventTime", List.of(
                        PlannedField.of("HostName", "keyword", "EventSource/Device/HostName")))),
                newIndexing, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH, "events", "EventTime", List.of(
                        PlannedField.of("host.name", "keyword", "EventSource/Device/HostName")))))));
        Mockito.when(documentAccess.canView(Mockito.any())).thenReturn(true);
        Mockito.when(indexingTrace.trace(Mockito.any(), Mockito.any())).thenReturn(IndexingTrace.Traced.NONE);
        // Its XML says the host; the document's own HostName is a relay's, never matched as a value.
        hosted("09:00", 31, Map.of("HostName", "relay-01", "host.name", "ws-a"));
        xml.put("31:" + eventId, "<Event><EventSource><Device><HostName>ws-a</HostName></Device></EventSource>"
                                 + "<EventDetail><View/></EventDetail></Event>");
        // No XML for this one: not read.
        hosted("09:05", 32, Map.of("HostName", "relay-02"));
        elementInIndex = true;

        final Map<String, Object> result = json(mcp(), arguments(Map.of("sources", List.of("Events"))));

        assertThat((List<String>) result.get("span_hosts")).contains("ws-a").doesNotContain("relay-01",
                "relay-02");
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat((Map<String, Object>) source.get("fields_by_pipeline")).containsEntry("host", Map.of(
                "EventSource/Device/HostName (the Events XML)", 1L, "not read", 1L));
        // Both events' XML asked for, once, for their fields.
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), Mockito.eq(Map.of(
                        31L, List.of((long) eventId - 1), 32L, List.of((long) eventId))), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments(Map.of("sources", List.of("Events"))), false)), chat(UserQuestions.NONE, null)).text();
        assertThat(text).contains("its feeding pipelines' plans differ, so each event's own was read: host by "
                                  + "EventSource/Device/HostName (the Events XML) 1, not read 1");
        // As check_skill reads a skill's runs back.
        final TimelineReadNotes.Seen seen = TimelineReadNotes.seen(List.of(text));
        assertThat(seen.fromXml()).isEqualTo(1);
        assertThat(seen.notRead()).isEqualTo(1);
        assertThat(seen.traced()).isZero();
    }

    @Test
    void aReprocessedEventsOldAndNewDocumentsAreCountedOnceByTheirRawStreamAndRecord() {
        // Raw stream 8 translated twice: Events streams 21 (old) and 22 (reprocessed), the same records 1 and 2.
        for (final long stream : new long[]{21, 22}) {
            events.add(new LinkedHashMap<>(Map.of("EventTime", at("09:00"), "UserId", "pat", "HostName",
                    "ws-" + stream, "SchemaAction", "View", "StreamId", stream, "EventId", 1L)));
            events.add(new LinkedHashMap<>(Map.of("EventTime", at("09:05"), "UserId", "pat", "HostName",
                    "ws-" + stream, "SchemaAction", "View", "StreamId", stream, "EventId", 2L)));
        }
        // Another raw stream's record 1 is another event.
        events.add(new LinkedHashMap<>(Map.of("EventTime", at("09:10"), "UserId", "pat", "HostName", "ws-30",
                "SchemaAction", "View", "StreamId", 30L, "EventId", 1L)));
        Mockito.when(streamProvenance.of(Mockito.any())).thenReturn(new Provenance(Map.of(
                21L, new StreamSource(21L, "ENDPOINT", "Events", null, null, 8L),
                22L, new StreamSource(22L, "ENDPOINT", "Events", null, null, 8L),
                30L, new StreamSource(30L, "ENDPOINT", "Events", null, null, 9L)), 0, Map.of()));
        elementInIndex = true;

        final Map<String, Object> result = json(mcp(), arguments(Map.of("sources", List.of("Events"))));

        assertThat(result).containsEntry("events_read", 3L);
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("duplicates_left_out", 2L).doesNotContainKey("not_deduplicated");
        // The reprocessed (newest) documents kept.
        assertThat(files.content(FOLDER + ActivityTimelineTool.EVENTS_FILE).orElseThrow()).contains(",22,1,",
                ",22,2,", ",30,1,").doesNotContain(",21,");
    }

    @Test
    void anIndexWithoutStreamIdsIsSaidNotDeduplicated() {
        code("09:00", "pat", "login");
        code("09:00", "pat", "login");
        final ModelSideCalls model = model(new ArrayList<>(), "{\"login\": {\"role\": \"active\", \"kind\": "
                                                              + "\"logon\"}}");

        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.eq(CODES))).thenReturn(Map.of(
                InstructionRules.EVENT_TYPE_FIELD, new FieldRule(List.of("code"), "/")));

        final Map<String, Object> result = json(mcpWith(model), arguments(Map.of("sources", List.of(Map.of(
                "index", "Codes", "user", "user")))));

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsKey("not_deduplicated").doesNotContainKey("duplicates_left_out");
        assertThat(result).containsEntry("events_read", 2L);
    }

    @Test
    void theXmlIsReadOnlyForWhatTheIndexDoesntMapAndOnlyForTheEventsCharacterised() {
        elementInIndex = true;
        week();

        // Not characterising: the index holds every event's type, so nothing is read from the XML.
        json(mcp(), arguments(Map.of()));
        Mockito.verifyNoInteractions(eventXml);

        // Characterising: the content the index doesn't map is read for those events alone, the paths named.
        final Map<String, Object> result = json(mcp(), arguments(Map.of("characterise", true)));
        final ArgumentCaptor<Map<Long, List<Long>>> ids = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(eventXml, Mockito.times(1)).read(Mockito.any(), ids.capture(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
        final int budget = new AiWorkspaceConfig().getCharacteriseMaxItems();
        assertThat(ids.getValue().values().stream().mapToInt(List::size).sum()).isEqualTo(Math.min(11, budget));
        final Map<String, Object> xmlRead = (Map<String, Object>) result.get("events_xml");
        assertThat((List<Map<String, Object>>) xmlRead.get("paths_read")).singleElement().satisfies(paths -> {
            assertThat(paths).containsEntry("index", "Events")
                    .containsEntry("paths", ActivityTimelineTool.CONTENT_PATHS);
            assertThat((String) paths.get("suggestion")).contains("index plan");
        });
    }

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
        if (elementInIndex) {
            doc.put("SchemaAction", element);
            if (action != null) {
                doc.put("event.action", action);
            }
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
        final Map<String, Object> doc = new LinkedHashMap<>(Map.of("EventTime", time, "UserId", user, "HostName",
                "ws-09", "StreamId", STREAM, "EventId", id));
        if (elementInIndex) {
            doc.put("SchemaAction", "View");
        }
        events.add(doc);
        xml.put(STREAM + ":" + id, "<Event><EventDetail><View><Resource><URL>" + url
                                   + "</URL></Resource></View></EventDetail></Event>");
    }

    private Listed skill(final String markdown) {
        final SkillDoc.Skill skill = SkillDoc.parse(markdown).skills().getFirst();
        final Listed listed = new Listed(SKILLS, ExplorerConstants.SYSTEM_DOC_REF, "/", skill, -1);
        Mockito.when(skillCatalogue.readable(Mockito.any())).thenReturn(List.of(listed));
        return listed;
    }

    // The sides read with their user field transformed: each a script over every event in the period.
    private List<ElasticKeyStreams.Side> scripted() {
        return streams.scanned().stream()
                .filter(side -> side.keys().stream().anyMatch(key -> !key.transforms().isEmpty()))
                .toList();
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

    // An event whose index holds its own type (its TypeId), and its object where given.
    private void typed(final String time, final long stream, final String typeId, final String url) {
        final long id = ++eventId;
        final Map<String, Object> doc = new LinkedHashMap<>(Map.of("EventTime", at(time), "UserId", "pat",
                "HostName", "ws-09", "TypeId", typeId, "StreamId", stream, "EventId", id));
        NullSafe.consume(url, value -> doc.put("url", value));
        events.add(doc);
    }

    // A View event of a stream with the host fields given.
    private void hosted(final String time, final long stream, final Map<String, String> hosts) {
        final long id = ++eventId;
        final Map<String, Object> doc = new LinkedHashMap<>(Map.of("EventTime", at(time), "UserId", "pat",
                "SchemaAction", "View", "StreamId", stream, "EventId", id));
        doc.putAll(hosts);
        events.add(doc);
    }

    // A model answering every side call with the text, each question kept.
    private static ModelSideCalls model(final List<String> asked, final String answer) {
        return new ModelSideCalls() {
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
                return new Answer(answer, 1, 1);
            }
        };
    }

    private ToolCallContext mcpWith(final ModelSideCalls sideCalls) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .sideCalls(sideCalls)
                .build();
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
