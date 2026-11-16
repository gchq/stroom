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

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEql.Event;
import stroom.ai.api.ElasticEql.Reason;
import stroom.ai.api.ElasticEql.Refusal;
import stroom.ai.api.ElasticEql.Request;
import stroom.ai.api.ElasticEql.Result;
import stroom.ai.api.ElasticEql.Sequence;
import stroom.ai.api.ElasticEql.Target;
import stroom.ai.api.ElasticEsql;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.EqlQueryWriter.Step;
import stroom.ai.impl.tools.EqlSequenceTool.Preflight;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class TestEqlSequenceTool {

    private static final DocRef DEVICES = new DocRef(ElasticIndexDoc.TYPE, "devices-uuid", "Devices");
    private static final DocRef SYSMON = new DocRef(ElasticIndexDoc.TYPE, "sysmon-uuid", "Sysmon");
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "lucene-uuid", "Lucene events");
    private static final long T0 = 1_767_225_600_000L;

    @TempDir
    Path tempDir;

    private final ChatWorkspace chatWorkspace = Mockito.mock(ChatWorkspace.class);
    private final ElasticEql elasticEql = Mockito.mock(ElasticEql.class);
    private final ElasticEsql elasticEsql = Mockito.mock(ElasticEsql.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final Map<String, String> written = new HashMap<>();
    private final Map<DocRef, Long> counts = new HashMap<>();
    private final List<Sequence> sequences = new ArrayList<>();
    private AiWorkspaceConfig config = new AiWorkspaceConfig();
    private EqlSequenceTool tool;

    @BeforeEach
    void setUp() throws IOException {
        Mockito.when(chatWorkspace.limits()).thenAnswer(invocation -> config);
        Mockito.when(chatWorkspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(chatWorkspace.tempFile()).thenAnswer(invocation -> Files.createTempFile(tempDir, "t", ".tmp"));
        Mockito.when(chatWorkspace.write(Mockito.anyInt(), Mockito.any(WorkspacePath.class), Mockito.any(Path.class),
                Mockito.any())).thenAnswer(invocation -> {
                    written.put(invocation.getArgument(1).toString(),
                            Files.readString(invocation.<Path>getArgument(2)));
                    return 1;
                });
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenAnswer(invocation -> {
            final String given = invocation.getArgument(0);
            return List.of(DEVICES, SYSMON, LUCENE).stream()
                    .filter(doc -> doc.getUuid().equals(given) || doc.getName().equals(given))
                    .toList();
        });
        Mockito.when(queryService.findFields(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            return new ResultPage<>(DEVICES.equals(doc)
                    ? List.of(QueryField.createDate("@timestamp"), QueryField.createKeyword("user"),
                    QueryField.createKeyword("host"), QueryField.createKeyword("action"),
                    QueryField.createIpV4Address("ip"), QueryField.createId("StreamId"), QueryField.createId("EventId"))
                    : List.of(QueryField.createDate("@timestamp"), QueryField.createKeyword("SubjectUserName"),
                            QueryField.createKeyword("Computer"), QueryField.createKeyword("Image"),
                            QueryField.createId("StreamId"), QueryField.createId("EventId")));
        });
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        Mockito.when(elasticEql.target(Mockito.any())).thenAnswer(invocation -> new Target("9.5.3", "@timestamp",
                invocation.<List<DocRef>>getArgument(0).stream().map(DocRef::getName).toList()));
        Mockito.when(elasticEql.search(Mockito.any())).thenAnswer(invocation -> {
            final Request request = invocation.getArgument(0);
            return new Result(request.query(), List.of("devices", "sysmon"), "@timestamp",
                    sequences.stream().limit(request.size()).toList(), 12, "9.5.3");
        });
        Mockito.when(elasticEsql.query(Mockito.any())).thenAnswer(invocation -> {
            final ElasticEsql.Request request = invocation.getArgument(0);
            return new ElasticEsql.Result("FROM x " + request.pipeline(), "x",
                    List.of(new ElasticEsql.Column("n", "long")),
                    List.of(List.of(counts.getOrDefault(request.index(), 10L))), 1, 1, null, false, false, null,
                    null, "9.5.3", List.of());
        });
        sequences.add(pair("alice", "H1", T0, 40_000, 1));
        tool = new EqlSequenceTool(chatWorkspace, () -> elasticEql, () -> elasticEsql, dataSourceAccess,
                () -> queryService, auditLog);
    }

    @Test
    void aSequenceAcrossTwoDocsRunsAsOneEqlSequence() {
        final String output = run(chat(UserQuestions.NONE), args("2m"));

        final Request request = request();
        assertThat(request.indexes()).containsExactly(DEVICES, SYSMON);
        assertThat(request.query()).isEqualTo("""
                sequence with maxspan=2m
                  [any where stroom_eql_doc == "1" and action : "copy"] by user, host
                  [any where stroom_eql_doc == "2" and Image like~ "*powershell.exe"] by SubjectUserName, Computer""");
        assertThat(request.fields()).containsExactly("StreamId", "EventId", "user", "host", "action",
                "SubjectUserName", "Computer");
        assertThat(request.size()).isEqualTo(EqlSequenceTool.DEFAULT_LIMIT);
        assertThat(request.timeRange().getFrom()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(output)
                .contains("with maxspan 2m (as asked)")
                .contains("Counted first: step 1: 10, step 2: 10 events.")
                .contains("1 sequence found, for 1 entity.")
                .contains("| user | host | sequences | first | last | shortest gap (s) |")
                .contains("| alice | H1 | 1 | 2026-01-01T00:00:00.000Z | 2026-01-01T00:00:40.000Z | 40 |")
                .contains("The sequences are in /outputs/eql/")
                .contains(EqlSequenceTool.CAVEAT)
                .contains("ask the user which doc to review (ask_user, the docs as options)")
                .contains("from \"Devices\"\nwhere (StreamId = 1 and EventId = 1)")
                .contains("It finds exactly the 1 hit event of this doc");
        assertThat(written.values()).singleElement().asString()
                .contains("| user | host | s1_time | s1_action | s1_StreamId | s1_EventId | gap_1_2_s | s2_time |");
        Mockito.verify(auditLog).searched(Mockito.any(), Mockito.eq(DEVICES),
                Mockito.argThat(query -> query.startsWith("sequence with maxspan=2m")
                                         && query.contains("count of step 1: | WHERE TO_LOWER(action) == \"copy\"")),
                Mockito.eq(1L), Mockito.eq("EQL sequence by the eql_sequence tool"));
        Mockito.verify(auditLog).searched(Mockito.any(), Mockito.eq(SYSMON), Mockito.anyString(), Mockito.eq(1L),
                Mockito.anyString());
    }

    @Test
    void overMcpTheResultIsJson() {
        final String output = run(mcp(), args("2m"));

        final Map<String, Object> json = JsonUtil.readValue(output, Map.class);
        assertThat(json).containsEntry("maxspan", "2m").containsEntry("sequence_count", 1)
                .containsEntry("truncated", false).containsEntry("caveat", EqlSequenceTool.CAVEAT);
        assertThat((List<Object>) json.get("step_counts")).containsExactly(10, 10);
        assertThat((List<Map<String, Object>>) json.get("review_queries")).hasSize(2)
                .allSatisfy(review -> assertThat(review).containsEntry("exact", true).containsEntry("valid", true));
        assertThat((String) json.get("review_hint")).contains("ask the user which doc to review");
        assertThat((List<List<String>>) json.get("rows")).singleElement().asList().contains("alice", "H1", "40");
    }

    @Test
    void withoutAWindowTheUserIsAskedInAForm() {
        final RecordingQuestions questions = new RecordingQuestions("1 minute");

        final String output = run(chat(questions), args(null));

        assertThat(questions.callId).isEqualTo("call-1#form:maxspan");
        assertThat(questions.intro).isEqualTo(EqlSequenceTool.MAXSPAN_QUESTION + " " + EqlSequenceTool.WIDER_NOTE)
                .doesNotContain("*", "#", "`");
        final Map<String, Object> field = (Map<String, Object>) ((Map<String, Object>) questions.form
                .get("properties")).get("maxspan");
        assertThat((List<String>) field.get("examples")).containsExactly("5 minutes (the default)", "1 minute",
                "15 minutes", "1 hour", EqlSequenceTool.ANOTHER_INTERVAL);
        assertThat(field).containsEntry("default", "5 minutes (the default)");
        assertThat(request().query()).startsWith("sequence with maxspan=1m");
        assertThat(output).contains("with maxspan 1m (the user's answer)");
    }

    @Test
    void theFormSuggestsTheConfiguredWindow() {
        config = new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, null, null, null, null,
                StroomDuration.ofMinutes(15));
        final RecordingQuestions questions = new RecordingQuestions("10m");

        run(chat(questions), args(null));

        final Map<String, Object> field = (Map<String, Object>) ((Map<String, Object>) questions.form
                .get("properties")).get("maxspan");
        assertThat((List<String>) field.get("examples")).containsExactly("15 minutes (the default)", "1 minute",
                "1 hour", EqlSequenceTool.ANOTHER_INTERVAL);
        // Typed in the user's own words.
        assertThat(request().query()).startsWith("sequence with maxspan=10m");
    }

    @Test
    void anotherIntervalNotGivenOrADeclineRunsNothing() {
        assertThat(run(chat(new RecordingQuestions(EqlSequenceTool.ANOTHER_INTERVAL)), args(null)))
                .contains("chose another interval but didn't give it");
        assertThat(run(chat(new RecordingQuestions(null)), args(null)))
                .contains("The user didn't choose a window");
        assertThat(run(chat(new RecordingQuestions("whenever")), args(null)))
                .contains("The user's window 'whenever' can't be read");
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void overMcpWithoutAWindowItIsRefusedWithTheQuestion() {
        final String output = run(mcp(), args(null));

        assertThat(output).startsWith("Error: Give maxspan")
                .contains(EqlSequenceTool.MAXSPAN_QUESTION)
                .contains("Suggest 5m (the default), or 1m, 15m, 1h, or another interval.")
                .contains(EqlSequenceTool.WIDER_NOTE);
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void refusals() {
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(DEVICES, "user")))))
                .contains("Give steps: 2 or more");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(LUCENE, "user"),
                step(DEVICES, "user"))))).contains("isn't an Elastic Index: EQL runs on Elasticsearch only. Use "
                                                    + "run_stroomql for a Lucene index.");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(DEVICES, "usr"),
                step(SYSMON, "SubjectUserName"))))).contains("Step 1: not fields of Elastic Index 'Devices': usr "
                                                              + "(like user)");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                step(DEVICES, "user", "host"), step(SYSMON, "SubjectUserName")))))
                .contains("Step 2 joins on [SubjectUserName] but step 1 on [user, host]");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                Map.of("index", "Devices", "by", List.of("user"), "missing", true, "runs", 3),
                step(SYSMON, "SubjectUserName"))))).contains("has missing=true and runs");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                Map.of("index", "Devices", "by", List.of()), step(SYSMON, "SubjectUserName")))))
                .contains("Step 1 has no join fields");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "25h", "steps", List.of(step(DEVICES, "user"),
                step(SYSMON, "SubjectUserName"))))).contains("The window may be at most 24h");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(DEVICES, "user"),
                step(DEVICES, "user")), "limit", 0))).doesNotContain("Error");
        Mockito.verify(elasticEql, Mockito.times(1)).search(Mockito.any());
    }

    @Test
    void aDocTheUserCantUseIsNamed() {
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(DEVICES, "user"),
                Map.of("index", "Secret", "by", List.of("x"))))))
                .contains("No Elastic Index doc with uuid or name 'Secret' that you can use");
        Mockito.doThrow(new PermissionException(null, "You are not authorised to search DocRef{type='ElasticIndex', "
                                                      + "uuid='sysmon-uuid', name='Sysmon'}"))
                .when(elasticEql).target(Mockito.any());
        assertThat(run(chat(UserQuestions.NONE), args("2m")))
                .contains("You may not use Elastic Index 'Sysmon' (sysmon-uuid)");
    }

    @Test
    void docsOnDifferentClustersOrTimeFieldsAreRefusedSayingWhatDiffers() {
        Mockito.doThrow(new Refusal(Reason.DIFFERENT_CLUSTERS, "The Elastic Indexes are on different clusters "
                                                                  + "('Devices' on cluster 'A', 'Sysmon' on cluster "
                                                                  + "'B'): an EQL sequence searches one cluster"))
                .doThrow(new Refusal(Reason.DIFFERENT_TIME_FIELDS, "The Elastic Indexes have different time "
                                                                     + "fields ('Devices' orders by 'EventTime', "
                                                                     + "'Sysmon' orders by '@timestamp')"))
                .doThrow(new Refusal(Reason.CLUSTER_TOO_OLD, "The cluster is Elasticsearch 7.9.0: EQL sequences "
                                                             + "need 7.10 or later"))
                .when(elasticEql).target(Mockito.any());

        assertThat(run(chat(UserQuestions.NONE), args("2m"))).contains("'Devices' on cluster 'A', 'Sysmon' on "
                                                                         + "cluster 'B'")
                .contains("Run a sequence per cluster");
        assertThat(run(chat(UserQuestions.NONE), args("2m"))).contains("'Devices' orders by 'EventTime'")
                .contains("Use docs whose time field is the same");
        assertThat(run(chat(UserQuestions.NONE), args("2m"))).contains("Use run_stroomql instead");
    }

    @Test
    void aClusterTooOldForAMissingStepIsRefused() {
        Mockito.doReturn(new Target("8.7.1", "@timestamp", List.of())).when(elasticEql).target(Mockito.any());

        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(DEVICES, "user"),
                Map.of("index", "Sysmon", "by", List.of("SubjectUserName"), "missing", true)))))
                .contains("Elasticsearch 8.7.1: a missing step (missing=true) needs 8.8 or later");
        // runs work from 8.7.
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                Map.of("index", "Devices", "by", List.of("user"), "runs", 3), step(SYSMON, "SubjectUserName")))))
                .doesNotContain("Error");
    }

    @Test
    void aClusterWithoutEsqlIsntCountedFirst() {
        Mockito.doReturn(new Target("8.12.0", "@timestamp", List.of())).when(elasticEql).target(Mockito.any());

        assertThat(run(chat(UserQuestions.NONE), args("2m")))
                .contains("The steps weren't counted first: that needs ES|QL, from Elasticsearch 8.14.");
        Mockito.verifyNoInteractions(elasticEsql);
    }

    @Test
    void aSequenceTheCountsSayIsTooBigIsRefusedBeforeItRuns() {
        counts.put(DEVICES, 9_000_000L);

        final String output = run(chat(UserQuestions.NONE), args("2m"));

        assertThat(output).contains("Not run: the steps match 9000010 events in the window (step 1: 9000000, step "
                                    + "2: 10)")
                .contains("longer than the 120-second time limit")
                .contains("Narrow the window or the first step")
                .contains("runAnyway=true");
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());

        final Map<String, Object> anyway = new LinkedHashMap<>(args("2m"));
        anyway.put("runAnyway", true);
        assertThat(run(chat(UserQuestions.NONE), anyway)).doesNotContain("Error");
        Mockito.verify(elasticEql).search(Mockito.any());
    }

    @Test
    void aTimeoutSaysToNarrowTheWindowOrTheFirstStep() {
        Mockito.doThrow(new Refusal(Reason.TIMEOUT, "The search took longer than 120 seconds, so it was stopped"))
                .when(elasticEql).search(Mockito.any());

        assertThat(run(chat(UserQuestions.NONE), args("2m"))).isEqualTo("Error: The search took longer than 120 "
                                                                          + "seconds, so it was stopped: narrow the "
                                                                          + "window or the first step.");
    }

    @Test
    void sequencesCutShortAtTheLimitSaySo() {
        sequences.add(pair("bob", "H2", T0, 1_000, 3));
        sequences.add(pair("carol", "H3", T0, 1_000, 5));
        final Map<String, Object> limited = new LinkedHashMap<>(args("2m"));
        limited.put("limit", 2);

        assertThat(run(chat(UserQuestions.NONE), limited)).contains("2 sequences found, for 2 entities.")
                .contains("They reached the limit (2), so they are cut short: these are the latest 2");
    }

    @Test
    void manyHitsStillGiveAnExactReviewWhileItFits() {
        sequences.clear();
        for (int i = 0; i < 101; i++) {
            sequences.add(pair("user" + (i % 7), "H1", T0 + i * 1_000L, 500, i * 10L));
        }

        final String output = run(chat(UserQuestions.NONE), args("2m"));

        assertThat(output).contains("It finds exactly the 101 hit events of this doc, by StreamId and EventId.")
                .contains("where (StreamId = 1 and EventId in (0, 10, 20,");
    }

    @Test
    void aReviewTooLongForTheLimitIsTheEntitiesInTheWindow() {
        config = new AiWorkspaceConfig(null, null, null, null, null, null, null, 400, null, null, null, null, null);
        sequences.clear();
        for (int i = 0; i < 101; i++) {
            sequences.add(pair("user" + (i % 7), "H1", T0 + i * 1_000L, 500, i * 10L));
        }

        final String output = run(chat(UserQuestions.NONE), args("2m"));

        assertThat(output).contains("Naming the hits by StreamId and EventId would be longer than a query may be "
                                    + "(400 characters), and the entities are too many to name")
                .contains("not only the hits.");
    }

    @Test
    void anIndexWithoutStreamIdsGetsTheWiderReviewSayingWhy() {
        Mockito.doAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            return new ResultPage<>(DEVICES.equals(doc)
                    ? List.of(QueryField.createDate("@timestamp"), QueryField.createKeyword("user"),
                    QueryField.createKeyword("host"), QueryField.createKeyword("action"),
                    QueryField.createId("StreamId"), QueryField.createId("EventId"))
                    : List.of(QueryField.createDate("@timestamp"), QueryField.createKeyword("SubjectUserName"),
                            QueryField.createKeyword("Computer"), QueryField.createKeyword("Image")));
        }).when(queryService).findFields(Mockito.any());

        final String output = run(chat(UserQuestions.NONE), args("2m"));

        assertThat(output).contains("This doc's index has no StreamId and EventId fields (it is filled from outside "
                                    + "Stroom), so its hits can't be named one by one, fetched with fetch_events or "
                                    + "stepped, so it finds the 1 entity's events in the window")
                .contains("from \"Sysmon\"\nwhere @timestamp >= 2026-01-01T00:00:40.000Z and @timestamp <= "
                          + "2026-01-01T00:00:40.000Z\nand ((SubjectUserName = \"alice\" and Computer = \"H1\"))\n"
                          + "sort by @timestamp\nselect @timestamp, SubjectUserName, Computer")
                .contains("from \"Devices\"\nwhere (StreamId = 1 and EventId = 1)");
    }

    @Test
    void theDescriptionSaysToAskWhichDocToReview() {
        final ToolDefinition definition = tool.definition();

        assertThat(definition.description()).contains("Each reads one doc: when the hunt spans several, ask the "
                                                       + "user which doc to review (ask_user, the docs as options), "
                                                       + "then offer that doc's.")
                .contains("a dashboard with the event preview (propose_dashboard)")
                .doesNotContain("stepping.");
        assertThat(definition.general().description()).contains("ask the user which doc to review, then offer "
                                                                 + "that doc's").doesNotContain("ask_user");
    }

    @Test
    void anInvalidReviewQueryIsntOffered() {
        Mockito.doReturn(new ValidateExpressionResult(false, "Unexpected token", false))
                .when(queryService).validateQuery(Mockito.anyString());

        assertThat(run(chat(UserQuestions.NONE), args("2m")))
                .contains("It isn't valid StroomQL (Unexpected token), so don't offer it.");
    }

    @Test
    void theCountsSayWhatToDo() {
        final List<Step> steps = List.of(stepOf(false, 1), stepOf(false, 1), stepOf(false, 1));

        final Preflight selective = EqlSequenceTool.assess(steps, List.of(50_000L, 4_000L, 200L),
                List.of("a", "b", "c"), Duration.ofMinutes(2));
        assertThat(selective.notes()).containsExactly("Step 3 matches far fewer events (200) than step 1 (50000): if "
                                                      + "the order doesn't matter to the question, put step 3 first, "
                                                      + "which is quicker.");
        assertThat(selective.tooBig()).isFalse();

        // Not for a small first step, nor where a missing step or runs fix the order.
        assertThat(EqlSequenceTool.assess(steps, List.of(900L, 10L, 10L), List.of("a", "b", "c"),
                Duration.ofMinutes(2)).notes()).isEmpty();
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(true, 1), stepOf(false, 1)),
                Arrays.asList(50_000L, null, 10L), Arrays.asList("a", null, "c"),
                Duration.ofMinutes(2)).notes()).isEmpty();
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 20)), List.of(50_000L, 10L),
                List.of("a", "b"), Duration.ofMinutes(2)).notes()).noneMatch(note -> note.contains("far fewer"));

        // A step with too few events to make one.
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 20)), List.of(5L, 10L),
                List.of("a", "b"), Duration.ofMinutes(2)).notes()).singleElement().asString()
                .contains("Step 2 matches 10 events in the window (it needs 20 in a row)");

        // Too big: the sum of the counts at EVENTS_PER_SECOND, over the time limit.
        final Preflight big = EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 1)),
                List.of(5_000_000L, 1_000_001L), List.of("a", "b"), Duration.ofMinutes(2));
        assertThat(big.tooBig()).isTrue();
        assertThat(big.tooBigNote()).contains("about 120 seconds");
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 1)),
                List.of(5_000_000L, 1_000_000L), List.of("a", "b"), Duration.ofMinutes(2)).tooBig()).isFalse();
    }

    private static Step stepOf(final boolean missing, final int runs) {
        return new Step(1, List.of(new Filter("user", Condition.EQUALS, "x", false)), List.of("user"), missing, runs,
                List.of(), Map.of());
    }

    // The clipboard-copy-then-PowerShell hunt across the two docs.
    private static Map<String, Object> args(final String maxspan) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("steps", List.of(
                Map.of("index", "Devices", "by", List.of("user", "host"), "fields", List.of("action"),
                        "where", List.of(Map.of("field", "action", "value", "copy", "caseInsensitive", true))),
                Map.of("index", "sysmon-uuid", "by", List.of("SubjectUserName", "Computer"),
                        "where", List.of(Map.of("field", "Image", "condition", "ENDS_WITH", "value",
                                "powershell.exe", "caseInsensitive", true)))));
        if (maxspan != null) {
            args.put("maxspan", maxspan);
        }
        args.put("timeFrom", "2026-01-01T00:00:00Z");
        return args;
    }

    private static Map<String, Object> step(final DocRef doc, final String... by) {
        return Map.of("index", doc.getName(), "by", List.of(by));
    }

    private static Sequence pair(final String user, final String host, final long time, final long gapMs,
                                 final long eventId) {
        return new Sequence(List.of(user, host), List.of(
                event(time, 1, eventId, Map.of("user", user, "host", host, "action", "Copy")),
                event(time + gapMs, 2, eventId + 1, Map.of("SubjectUserName", user, "Computer", host))));
    }

    private static Event event(final long time, final long streamId, final long eventId,
                               final Map<String, Object> fields) {
        final Map<String, List<Object>> all = new HashMap<>();
        all.put("@timestamp", List.of(time));
        all.put("StreamId", List.of(streamId));
        all.put("EventId", List.of(eventId));
        fields.forEach((name, value) -> all.put(name, List.of(value)));
        return new Event("index", "id", false, all);
    }

    private Request request() {
        final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Mockito.verify(elasticEql).search(captor.capture());
        return captor.getValue();
    }

    private String run(final ToolCallContext context, final Map<String, Object> args) {
        return tool.execute(new ToolCall("call-1", EqlSequenceTool.NAME, JsonUtil.writeValueAsString(args)), context)
                .text();
    }

    private ToolCallContext chat(final UserQuestions questions) {
        return ToolCallContext.builder(ToolSession.chat(1))
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .userQuestions(questions)
                .limits(SessionLimits.lookedUp(() -> config))
                .build();
    }

    private ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .userQuestions(new RecordingQuestions("1 minute"))
                .limits(SessionLimits.lookedUp(() -> config))
                .build();
    }


    // --------------------------------------------------------------------------------


    /// Answers the form with a set answer (null to decline), recording what was asked.
    private static final class RecordingQuestions implements UserQuestions {

        private final String answer;
        private String callId;
        private String intro;
        private Map<String, Object> form;

        private RecordingQuestions(final String answer) {
            this.answer = answer;
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
            this.callId = callId;
            this.intro = intro;
            this.form = form;
            return answer == null
                    ? new ElicitResult(ElicitResult.Action.DECLINE, null)
                    : new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("maxspan", answer));
        }
    }
}
