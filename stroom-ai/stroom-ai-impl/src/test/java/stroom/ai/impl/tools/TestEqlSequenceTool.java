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
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.TaskTerminatedException;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import dev.langchain4j.internal.JsonSchemaElementUtils;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.assertj.core.api.InstanceOfAssertFactories;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                  [any where action : "copy"] by user, host
                  [any where Image like~ "*powershell.exe"] by SubjectUserName, Computer""");
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
        // The count reads at most COUNT_CAP + 1 events: more than the cap.
        counts.put(DEVICES, EqlSequenceTool.COUNT_CAP + 1);

        final String output = run(chat(UserQuestions.NONE), args("2m"));

        assertThat(output).contains("Not run: step 1 matches more than 1000000 events in the window (step 1: more "
                                    + "than 1000000, step 2: 10), and a sequence reads every event of its first "
                                    + "step, so it is unlikely to finish in the time limit.")
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
    void theRunsStopReachesTheSearchAndAStoppedSearchEndsTheRunStopped() {
        Mockito.doThrow(new Refusal(ElasticEql.Reason.STOPPED,
                "The search was stopped, so it was deleted in Elasticsearch")).when(elasticEql).search(Mockito.any());
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        assertThatThrownBy(() -> run(inRun(cancelled), args("2m")))
                .isInstanceOf(TaskTerminatedException.class);

        // The search and the counts before it are given the run's stop.
        cancelled.set(true);
        assertThat(request().stopped().getAsBoolean()).isTrue();
        final ArgumentCaptor<ElasticEsql.Request> counts = ArgumentCaptor.forClass(ElasticEsql.Request.class);
        Mockito.verify(elasticEsql, Mockito.atLeastOnce()).query(counts.capture());
        assertThat(counts.getAllValues()).allSatisfy(count -> assertThat(count.stopped().getAsBoolean()).isTrue());
    }

    @Test
    void aStopWhileTheStepsAreCountedDoesntRunTheSequence() {
        // Before, a refused count was only noted ("the steps weren't counted first") and the sequence ran anyway.
        Mockito.doThrow(new ElasticEsql.Refusal(ElasticEsql.Reason.STOPPED,
                "The query was stopped, so it was cancelled in Elasticsearch")).when(elasticEsql).query(Mockito.any());

        assertThatThrownBy(() -> run(inRun(new AtomicBoolean(true)), args("2m")))
                .isInstanceOf(TaskTerminatedException.class);
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void overMcpTheSearchIsNeverStopped() {
        run(mcp(), args("2m"));

        assertThat(request().stopped().getAsBoolean()).isFalse();
    }

    @Test
    void aTextFieldIsSearchedAsItsKeywordSubfieldWhateverItsName() {
        // Not ".keyword": the subfield is looked up in the mapping. Of two keyword subfields, the one without a
        // normaliser is taken.
        withDevicesMapping(List.of(
                new ElasticEql.Field("message", "text", null, null, false),
                new ElasticEql.Field("message.lower", "keyword", "message", "lowercase", false),
                new ElasticEql.Field("message.raw", "keyword", "message", null, false),
                new ElasticEql.Field("message.words", "text", "message", null, false)));

        final String output = run(chat(UserQuestions.NONE), messageArgs(Map.of("field", "message",
                "condition", "CONTAINS", "value", "usb")));

        assertThat(request().query()).contains("message.raw like \"*usb*\"").doesNotContain("message like");
        assertThat(output).contains("'message' in Elastic Index 'Devices' is a text field, which EQL can't search, "
                                    + "so its subfield 'message.raw' (keyword) was searched instead");
    }

    @Test
    void aNormalisedKeywordSubfieldIsTakenWhenItIsTheOnlyOne() {
        withDevicesMapping(List.of(
                new ElasticEql.Field("message", "text", null, null, false),
                new ElasticEql.Field("message.lower", "keyword", "message", "lowercase", false)));

        final String output = run(chat(UserQuestions.NONE), messageArgs(Map.of("field", "message",
                "value", "usb")));

        assertThat(request().query()).contains("message.lower == \"usb\"");
        assertThat(output).contains("its subfield 'message.lower' (keyword, normaliser lowercase)");
    }

    @Test
    void aTextFieldWithNoKeywordSubfieldIsRefusedNamingItsSubfields() {
        withDevicesMapping(List.of(
                new ElasticEql.Field("message", "text", null, null, false),
                new ElasticEql.Field("message.words", "text", "message", null, false)));

        assertThat(run(chat(UserQuestions.NONE), messageArgs(Map.of("field", "message", "value", "usb"))))
                .contains("Step 1: 'message' in Elastic Index 'Devices' is a text field, which EQL can't search, "
                          + "and it has no keyword subfield (its subfields: message.words (text))");
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void aTextFieldWithNoSubfieldsIsRefused() {
        withDevicesMapping(List.of(new ElasticEql.Field("message", "text", null, null, false)));

        assertThat(run(chat(UserQuestions.NONE), messageArgs(Map.of("field", "message", "value", "usb"))))
                .contains("is a text field, which EQL can't search, and it has no subfields");
    }

    @Test
    void aNestedFieldIsRefusedUpFront() {
        withDevicesMapping(List.of(
                new ElasticEql.Field("message", "keyword", null, null, true)));

        assertThat(run(chat(UserQuestions.NONE), messageArgs(Map.of("field", "message", "value", "usb"))))
                .contains("Step 1: 'message' in Elastic Index 'Devices' is in a nested field, which EQL can't "
                          + "search");
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void anyOfNotInAndLengthAreWrittenIntoTheStep() {
        final Map<String, Object> args = args("2m");
        final List<Map<String, Object>> steps = new ArrayList<>((List<Map<String, Object>>) args.get("steps"));
        final Map<String, Object> first = new LinkedHashMap<>(steps.getFirst());
        first.put("where", List.of(
                Map.of("field", "user", "condition", "NOT_IN", "value", "svc,system", "caseInsensitive", true),
                Map.of("field", "action", "condition", "GREATER_THAN", "value", "100", "length", true)));
        first.put("any_of", List.of(Map.of("field", "host", "value", "H1"), Map.of("field", "host", "value", "H2")));
        steps.set(0, first);
        args.put("steps", steps);

        run(chat(UserQuestions.NONE), args);

        assertThat(request().query()).contains("[any where not user in~ (\"svc\", "
                                               + "\"system\") and length(action) > 100 and (host == \"H1\" or "
                                               + "host == \"H2\")] by user, host");
    }

    @Test
    void anyOfIsOneLevelOfTwoOrMoreConditionsOnTheStepsFields() {
        final Map<String, Object> args = args("2m");
        final List<Map<String, Object>> steps = new ArrayList<>((List<Map<String, Object>>) args.get("steps"));
        final Map<String, Object> first = new LinkedHashMap<>(steps.getFirst());

        first.put("any_of", List.of(Map.of("field", "host", "value", "H1")));
        steps.set(0, first);
        args.put("steps", steps);
        assertThat(run(chat(UserQuestions.NONE), args)).contains("Step 1's any_of has one condition: give 2 or more");

        first.put("any_of", List.of(Map.of("field", "host", "value", "H1"), Map.of("any_of", List.of())));
        args.put("steps", List.of(first, steps.get(1)));
        assertThat(run(chat(UserQuestions.NONE), args)).contains("has a condition with its own any_of");

        first.put("any_of", List.of(Map.of("field", "host", "value", "H1"), Map.of("field", "hots", "value", "x")));
        args.put("steps", List.of(first, steps.get(1)));
        assertThat(run(chat(UserQuestions.NONE), args)).contains("not fields of Elastic Index 'Devices': hots");
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void aLiteralQuestionMarkInAPatternIsRefused() {
        assertThat(run(chat(UserQuestions.NONE), messageArgs(Map.of("field", "action", "condition", "CONTAINS",
                "value", "what?"))))
                .contains("has a ? in a pattern ('what?')");
    }

    // The steps of args("2m"), with step 1's where replaced by one condition.
    private static Map<String, Object> messageArgs(final Map<String, Object> condition) {
        final Map<String, Object> args = args("2m");
        final List<Map<String, Object>> steps = new ArrayList<>((List<Map<String, Object>>) args.get("steps"));
        final Map<String, Object> first = new LinkedHashMap<>(steps.getFirst());
        first.put("where", List.of(condition));
        first.remove("fields");
        steps.set(0, first);
        args.put("steps", steps);
        return args;
    }

    // Gives Devices a message field (and its subfields) with these mappings, as the target and as its fields.
    private void withDevicesMapping(final List<ElasticEql.Field> mapped) {
        final Map<String, ElasticEql.Field> byName = new LinkedHashMap<>();
        mapped.forEach(field -> byName.put(field.name(), field));
        Mockito.doAnswer(invocation -> new Target("9.5.3", "@timestamp", List.of("devices", "sysmon"),
                List.of(byName, Map.of()))).when(elasticEql).target(Mockito.any());
        final List<QueryField> devicesFields = new ArrayList<>(List.of(QueryField.createDate("@timestamp"),
                QueryField.createKeyword("user"), QueryField.createKeyword("host"),
                QueryField.createKeyword("action"), QueryField.createId("StreamId"),
                QueryField.createId("EventId")));
        mapped.forEach(field -> devicesFields.add("text".equals(field.nativeType())
                ? QueryField.createText(field.name())
                : QueryField.createKeyword(field.name())));
        Mockito.doAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            return new ResultPage<>(DEVICES.equals(doc)
                    ? devicesFields
                    : List.of(QueryField.createDate("@timestamp"), QueryField.createKeyword("SubjectUserName"),
                            QueryField.createKeyword("Computer"), QueryField.createKeyword("Image"),
                            QueryField.createId("StreamId"), QueryField.createId("EventId")));
        }).when(queryService).findFields(Mockito.any());
    }

    @Test
    void aStepIsKeptToItsDocOnlyWhenItsConditionsCouldMatchAnotherDocsEvents() {
        // Devices' action and Sysmon's Image are each mapped in one doc only: no doc condition, no script. StreamId
        // is in both, so a step on it alone is kept to its doc; so is one with only a negated condition, which an
        // event without the field can meet.
        run(chat(UserQuestions.NONE), args("2m"));
        assertThat(request().query()).doesNotContain(ElasticEql.DOC_FIELD);

        final Map<String, Object> shared = args("2m");
        final List<Map<String, Object>> steps = new ArrayList<>((List<Map<String, Object>>) shared.get("steps"));
        final Map<String, Object> first = new LinkedHashMap<>(steps.getFirst());
        first.put("where", List.of(Map.of("field", "StreamId", "value", "1")));
        first.remove("fields");
        steps.set(0, first);
        final Map<String, Object> second = new LinkedHashMap<>(steps.get(1));
        second.put("where", List.of(Map.of("field", "Image", "condition", "NOT_IN", "value", "cmd.exe")));
        steps.set(1, second);
        shared.put("steps", steps);
        Mockito.clearInvocations(elasticEql);

        run(chat(UserQuestions.NONE), shared);

        assertThat(request().query()).isEqualTo("""
                sequence with maxspan=2m
                  [any where stroom_eql_doc == "1" and StreamId == 1] by user, host
                  [any where stroom_eql_doc == "2" and not Image in ("cmd.exe")] by SubjectUserName, Computer""");
    }

    @Test
    void theResultSaysTheSequencesAreAtLeastThese() {
        final String output = run(chat(UserQuestions.NONE), args("2m"));

        assertThat(output).contains("1 sequence found, for 1 entity. " + EqlSequenceTool.AT_LEAST)
                .contains("By entity (sequences found, at least these):");
        assertThat(JsonUtil.readValue(run(mcp(), args("2m")), Map.class).get("note").toString())
                .contains(EqlSequenceTool.AT_LEAST);
    }

    @Test
    void theDescriptionLeadsWithTheOrderedQuestionsItAnswers() {
        // Seen in acceptance runs on a small model: ordered questions answered by joining esql_query results.
        final String lead = "Use for any 'X, then Y, by the same user/host, within N' question on Elastic Indexes, "
                            + "in one doc or across several, rather than joining the results of separate queries";

        assertThat(tool.definition().description()).startsWith(lead + " (esql_query, search_events) by hand.");
        // Over MCP, without the chat's tool names.
        assertThat(tool.definition().general().description()).startsWith(lead + " by hand.");
    }

    @Test
    void untilAndAnyOfListTheirProperties() {
        // Before, until and the any_of items were objects with no properties listed, which strict providers may
        // refuse, and which told the model nothing of what goes in them.
        final Map<String, Object> parameters = JsonSchemaElementUtils.toMap(
                LangChain4jTools.specification(tool.definition()).parameters());
        final Map<String, Object> properties = map(parameters.get("properties"));
        final Map<String, Object> step = map(map(map(properties.get("steps")).get("items")).get("properties"));
        final Map<String, Object> until = map(properties.get("until"));
        final Map<String, Object> condition = map(map(step.get("where")).get("items"));

        assertThat(map(until.get("properties"))).containsOnlyKeys("index", "where", "any_of", "by");
        assertThat(until.get("required")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("index", "by");
        assertThat(map(map(step.get("any_of")).get("items")).get("properties")).isEqualTo(condition.get("properties"));
        assertThat(map(map(map(until.get("properties")).get("any_of")).get("items")).get("properties"))
                .isEqualTo(condition.get("properties"));
        assertThat(map(map(map(until.get("properties")).get("where")).get("items")).get("properties"))
                .isEqualTo(condition.get("properties"));
        assertThat(map(condition.get("properties"))).containsOnlyKeys("field", "condition", "value",
                "caseInsensitive", "length");
    }

    @Test
    void theDefinitionIsFullNowItIsOfferedOnlyWhenItsGroupIsOn() {
        // Offered only once the model turns on elastic_hunting, so the rules and examples are back in it; they are
        // also said by the refusal or result that meets them (see theRulesAreAlsoSaidWhereACallMeetsThem).
        final String description = tool.definition().description();

        assertThat(description).contains("20 failed logons then a success")
                .contains("A literal ? can't be in a pattern")
                .contains("nested fields")
                .contains("keyword subfield")
                .contains("Refused for a Lucene index (use run_stroomql)")
                .contains("runAnyway=true")
                .contains("ask the user which doc to review (ask_user, the docs as options)")
                .contains("can't see");
    }

    @Test
    void theRulesAreAlsoSaidWhereACallMeetsThem() {
        // Each is said by the refusal or result that meets it, as the tests here show: a literal ? in a pattern
        // (aLiteralQuestionMarkInAPatternIsRefused), nested and text fields (aNestedFieldIsRefusedUpFront,
        // aTextFieldWithNoKeywordSubfieldIsRefusedNamingItsSubfields), the versions for missing and runs
        // (aClusterTooOldForAMissingStepIsRefused), a Lucene index (refusals), docs on different clusters
        // (docsOnDifferentClustersOrTimeFieldsAreRefusedSayingWhatDiffers), the counts and runAnyway
        // (aSequenceTheCountsSayIsTooBigIsRefusedBeforeItRuns), and the caveat and which doc to review (the
        // result, aSequenceAcrossTwoDocsRunsAsOneEqlSequence).
        assertThat(EqlSequenceTool.reviewHint(2)).contains("ask the user which doc to review (ask_user, the docs as "
                                                           + "options), then offer that doc's with propose_query, or "
                                                           + "propose_dashboard for a table with the event preview.");
        assertThat(EqlSequenceTool.reviewHint(1)).contains("offer it with propose_query, or propose_dashboard");
    }

    @Test
    void sequencesWithNoTimeAreCounted() {
        assertThat(EqlSequenceTool.untimedNote(2, 5)).startsWith("2 of the 5 sequences came back with no time on "
                                                                 + "their events")
                .contains("their times and gaps are empty");
        assertThat(EqlSequenceTool.untimedNote(1, 1)).startsWith("1 of the 1 sequence came back with no time on its "
                                                                 + "events");
    }

    @Test
    void theLimitsLeftOutOfTheSchemaAreSaidWhenPassed() {
        final Map<String, Object> tooMany = new LinkedHashMap<>(args("2m"));
        tooMany.put("limit", 20_000);
        assertThat(run(chat(UserQuestions.NONE), tooMany)).contains("The limit asked for was over 10000, the most "
                                                                    + "allowed, so it was lowered.");

        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                Map.of("index", "Devices", "by", List.of("user"), "runs", 101), step(SYSMON, "SubjectUserName")))))
                .contains("Step 1: runs is a whole number, from 1 to 100.");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                Map.of("index", "Devices", "by", List.of("user"), "fields",
                        List.of("user", "host", "action", "ip", "StreamId", "EventId")),
                step(SYSMON, "SubjectUserName"))))).contains("Step 1 asks for 6 fields: show at most 5.");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(
                Map.of("index", "Devices", "by", List.of("user"), "any_of", List.of("host")),
                step(SYSMON, "SubjectUserName"))))).contains("Step 1's any_of is a list of {field, condition, value}");
        assertThat(run(chat(UserQuestions.NONE), Map.of("maxspan", "2m", "steps", List.of(step(DEVICES, "user"),
                step(SYSMON, "SubjectUserName")), "until", "nonsense")))
                .contains("The steps can't be read: give them as JSON objects.");
    }

    @Test
    void itIsUsableOnlyByAUserWhoCanUseAnElasticIndex() {
        Mockito.when(dataSourceAccess.canUseAny(ElasticIndexDoc.TYPE)).thenReturn(true);
        assertThat(tool.isUsable()).isTrue();

        Mockito.when(dataSourceAccess.canUseAny(ElasticIndexDoc.TYPE)).thenReturn(false);
        assertThat(tool.isUsable()).isFalse();
        // No cluster is called to tell.
        Mockito.verifyNoInteractions(elasticEql, elasticEsql);
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
                List.of("a", "b", "c"));
        assertThat(selective.notes()).containsExactly("Step 3 matches far fewer events (200) than step 1 (50000): if "
                                                      + "the order doesn't matter to the question, put step 3 first, "
                                                      + "which is quicker.");
        assertThat(selective.tooBig()).isFalse();

        // Not for a small first step, nor where a missing step or runs fix the order.
        assertThat(EqlSequenceTool.assess(steps, List.of(900L, 10L, 10L), List.of("a", "b", "c")).notes()).isEmpty();
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(true, 1), stepOf(false, 1)),
                Arrays.asList(50_000L, null, 10L), Arrays.asList("a", null, "c")).notes()).isEmpty();
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 20)), List.of(50_000L, 10L),
                List.of("a", "b")).notes()).noneMatch(note -> note.contains("far fewer"));

        // A step with too few events to make one.
        assertThat(EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 20)), List.of(5L, 10L),
                List.of("a", "b")).notes()).singleElement().asString()
                .contains("Step 2 matches 10 events in the window (it needs 20 in a row)");

        // Too big: the first step past the cap (a bounded count), whatever the later steps.
        final long capped = EqlSequenceTool.COUNT_CAP + 1;
        final Preflight big = EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 1)),
                List.of(capped, 10L), List.of("a", "b"));
        assertThat(big.tooBig()).isTrue();
        assertThat(big.tooBigNote()).contains("step 1 matches more than 1000000 events");
        // The selective-step advice still works from the bounded first count.
        assertThat(big.notes()).singleElement().asString().contains("Step 2 matches far fewer events (10) than step "
                                                                     + "1 (more than 1000000)");
        // A first step at the cap is not past it; a later step past it is only noted.
        final Preflight laterLarge = EqlSequenceTool.assess(List.of(stepOf(false, 1), stepOf(false, 1)),
                List.of(EqlSequenceTool.COUNT_CAP, capped), List.of("a", "b"));
        assertThat(laterLarge.tooBig()).isFalse();
        assertThat(laterLarge.notes()).containsExactly("Step 2 matches more than 1000000 events: if the sequence times "
                                                       + "out, narrow that step.");
        // A missing first step: the first step that must happen is judged.
        assertThat(EqlSequenceTool.assess(List.of(stepOf(true, 1), stepOf(false, 1)),
                Arrays.asList(null, capped), Arrays.asList(null, "b")).tooBig()).isTrue();
    }

    @Test
    void aShortTimeRangeIsntCountedFirst() {
        final Map<String, Object> shortRange = new LinkedHashMap<>(args("2m"));
        shortRange.put("timeTo", "2026-01-01T01:00:00Z");

        assertThat(run(chat(UserQuestions.NONE), shortRange))
                .contains("The steps weren't counted first: the time range is 1 hour or less, so the sequence is "
                          + "cheap enough to just run.");
        Mockito.verifyNoInteractions(elasticEsql);

        assertThat(EqlSequenceTool.isShort(new TimeRange(null, "2026-01-01T00:00:00Z", "2026-01-01T01:00:01Z")))
                .isFalse();
        assertThat(EqlSequenceTool.isShort(new TimeRange(null, "now() - 30m", "now()"))).isTrue();
        assertThat(EqlSequenceTool.isShort(new TimeRange(null, "2026-01-01T00:00:00Z", null))).isFalse();
        assertThat(EqlSequenceTool.isShort(null)).isFalse();
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

    // A chat's call in an agent run, stopped when `cancelled` is set.
    private ToolCallContext inRun(final AtomicBoolean cancelled) {
        return ToolCallContext.builder(ToolSession.chat(1))
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(() -> config))
                .runContext(new ToolRunContext(null, null, cancelled::get))
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
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
