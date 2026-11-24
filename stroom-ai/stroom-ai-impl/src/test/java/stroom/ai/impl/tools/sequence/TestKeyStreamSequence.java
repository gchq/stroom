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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticKeyStreams.Side;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.query.AgentSearches;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests `sequence_query`'s streaming engine ([KeyStreamSequence]) through the tool, over [InMemoryKeyStreams]:
/// a join past EQL's 10,000, workers giving the same result as one, a small side pushed down, the caps and the
/// estimate, the diagnosis of mismatched values and the transform that bridges them, keys that began and never
/// ended, a team filter, and the engine chosen by itself where EQL can't run the sequence.
@SuppressWarnings("unchecked")
class TestKeyStreamSequence {

    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    private static final DocRef ENDPOINT = new DocRef(ElasticIndexDoc.TYPE, "endpoint-uuid", "Endpoint");
    private static final DocRef USERS = new DocRef(ElasticIndexDoc.TYPE, "users-uuid", "Users");
    private static final long T0 = 1_767_225_600_000L;
    private static final Pattern BY = Pattern.compile("BY (.+?) \\|");

    @TempDir
    Path tempDir;

    private final ChatWorkspace chatWorkspace = Mockito.mock(ChatWorkspace.class);
    private final ElasticEql elasticEql = Mockito.mock(ElasticEql.class);
    private final ElasticEsql elasticEsql = Mockito.mock(ElasticEsql.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final Map<String, String> written = new HashMap<>();
    private final InMemoryKeyStreams streams = new InMemoryKeyStreams();
    private AiWorkspaceConfig config = config(1, 0, 1_000_000L, 10_000_000L);
    private SequenceQueryTool tool;
    private List<Map<String, Object>> proxy;
    private List<Map<String, Object>> endpoint;
    private List<Map<String, Object>> users;

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
            return List.of(PROXY, ENDPOINT, USERS).stream()
                    .filter(doc -> doc.getUuid().equals(given) || doc.getName().equals(given))
                    .toList();
        });
        Mockito.when(queryService.findFields(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            if (USERS.equals(doc)) {
                return new ResultPage<>(List.of(QueryField.createKeyword("sAMAccountName"),
                        QueryField.createKeyword("team")));
            } else if (ENDPOINT.equals(doc)) {
                return new ResultPage<>(List.of(QueryField.createDate("EventTime"),
                        QueryField.createKeyword("UserName"),
                        QueryField.createKeyword("action"), QueryField.createKeyword("file"),
                        QueryField.createId("StreamId"), QueryField.createId("EventId")));
            }
            return new ResultPage<>(List.of(QueryField.createDate("@timestamp"), QueryField.createKeyword("user"),
                    QueryField.createKeyword("action"), QueryField.createKeyword("url"),
                    QueryField.createId("StreamId"), QueryField.createId("EventId")));
        });
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        Mockito.when(elasticEql.target(Mockito.any())).thenAnswer(invocation -> new ElasticEql.Target("9.5.3",
                "@timestamp", invocation.<List<DocRef>>getArgument(0).stream().map(DocRef::getName).toList()));
        Mockito.when(elasticEsql.query(Mockito.any())).thenAnswer(invocation -> estimate(invocation.getArgument(0)));
        proxy = streams.add(PROXY, "@timestamp", Map.of("@timestamp", "date", "user", "keyword", "action",
                "keyword", "url", "keyword", "StreamId", "long", "EventId", "long"));
        endpoint = streams.add(ENDPOINT, "EventTime", Map.of("EventTime", "date", "UserName", "keyword", "action",
                "keyword", "file", "keyword", "StreamId", "long", "EventId", "long"));
        users = streams.add(USERS, null, Map.of("sAMAccountName", "keyword", "team", "keyword"));
        tool = new SequenceQueryTool(chatWorkspace, () -> elasticEql, () -> elasticEsql, dataSourceAccess,
                () -> queryService, auditLog, Mockito.mock(AgentSearches.class),
                new KeyStreamSequence(() -> streams, () -> elasticEsql, securityContext, auditLog),
                new TimelineFields(Mockito.mock(AgentInstructions.class), dataSourceAccess, Optional.empty()));
    }

    @Test
    void aJoinPastTenThousandSequencesCountsEveryOneAndKeepsTheLimit() {
        downloadsThenCopies(15_000, 30_000);

        final Map<String, Object> result = json(mcp(), downloadThenCopy("stream", null));

        assertThat(result).containsEntry("engine", "stream").containsEntry("sequence_count", 15_000L)
                .containsEntry("entity_count", 15_000L).containsEntry("truncated", true)
                .containsEntry("limit", (long) KeyStreamSequence.DEFAULT_LIMIT);
        assertThat((String) result.get("engine_reason")).isEqualTo(SequenceQueryTool.STREAM_ASKED);
        assertThat((String) result.get("note")).contains("Counted exactly").contains("don't present them as all");
        assertThat(((Number) result.get("rows_read")).longValue()).isEqualTo(30_000L);
        // Every sequence's events, by step, are in the table: 10,000 rows and a heading.
        final String table = written.get(String.valueOf(result.get("table")));
        assertThat(table.lines().count()).isEqualTo(KeyStreamSequence.DEFAULT_LIMIT + 2);
        assertThat(streams.open()).isZero();
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
        Mockito.verify(auditLog, Mockito.times(2)).searched(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyLong(), Mockito.contains("Streaming join"));
    }

    @Test
    void fourWorkersGiveTheSameSequencesAsOne() {
        downloadsThenCopies(30_000, 30_000);

        final Map<String, Object> one = json(mcp(), downloadThenCopy("stream", 100_000));
        final String oneTable = written.get(String.valueOf(one.get("table")));
        config = config(4, 0, 1_000_000L, 10_000_000L);
        written.clear();
        final Map<String, Object> four = json(mcp(), downloadThenCopy("stream", 100_000));

        assertThat(one).containsEntry("workers", 1L).containsEntry("key_ranges", 1L);
        assertThat(four).containsEntry("workers", 4L).containsEntry("key_ranges", 4L);
        assertThat((String) four.get("ranges_note")).contains("4 ranges of about the same events");
        assertThat(four.get("sequence_count")).isEqualTo(one.get("sequence_count")).isEqualTo(30_000L);
        assertThat(written.get(String.valueOf(four.get("table")))).isEqualTo(oneTable);
        // Each range scanned each side once, restricted to its range.
        assertThat(streams.scanned().stream().filter(side -> !side.range().all()).count()).isEqualTo(8);
    }

    @Test
    void aTeamsUsersArePushedDownAsTermsSoTheLargeSideIsCutBeforeItStreams() {
        config = config(4, 50_000, 1_000_000L, 10_000_000L);
        downloadsThenCopies(5_000, 0);
        for (int i = 0; i < 20; i++) {
            users.add(Map.of("sAMAccountName", user(i * 7), "team", i < 5
                    ? "A"
                    : "B"));
        }

        final Map<String, Object> result = json(mcp(), Map.of("engine", "stream", "steps", List.of(
                Map.of("index", "Users", "by", List.of("sAMAccountName"), "keysOnly", true,
                        "where", List.of(Map.of("field", "team", "value", "A"))),
                Map.of("index", "Proxy", "by", List.of("user"), "where", List.of(Map.of("field", "action",
                        "value", "download"))))));

        assertThat(result).containsEntry("sequence_count", 5L).containsEntry("entity_count", 5L);
        assertThat((String) result.get("pushed_down")).contains("Step 1 has 5 keys");
        // Each worker's range read its own part of the team's users as terms.
        final List<Object> terms = streams.scanned().stream()
                .filter(side -> side.index().equals(PROXY))
                .flatMap(side -> side.terms().getFirst().stream())
                .toList();
        assertThat(terms).containsExactlyInAnyOrder(user(0), user(7), user(14), user(21), user(28));
        assertThat(result).containsEntry("workers", 4L).containsEntry("key_ranges", 4L);
        // The team's keys and their 5 downloads, not the proxy's 5,000.
        assertThat(((Number) result.get("rows_read")).longValue()).isEqualTo(10L);
        assertThat(result).doesNotContainKey("maxspan");
    }

    @Test
    void aMissingKeysStepLeavesOutTheTeamsUsers() {
        downloadsThenCopies(10, 0);
        users.add(Map.of("sAMAccountName", user(3), "team", "A"));

        final Map<String, Object> result = json(mcp(), Map.of("steps", List.of(
                Map.of("index", "Proxy", "by", List.of("user"), "where", List.of(Map.of("field", "action",
                        "value", "download"))),
                Map.of("index", "Users", "by", List.of("sAMAccountName"), "keysOnly", true, "missing", true))));

        assertThat(result).containsEntry("engine", "stream").containsEntry("sequence_count", 9L);
        assertThat((String) result.get("engine_reason")).isEqualTo(SequenceQueryTool.STREAM_NEEDED);
    }

    @Test
    void aJoinNearTheCapsIsRefusedWithTheEstimateWhereNoFormCanBeShown() {
        downloadsThenCopies(9_000, 9_000);
        config = config(2, 10, 1_000_000L, 20_000L);

        final String refused = text(mcp(), downloadThenCopy("stream", null));

        assertThat(refused).contains("Not run: the join is near its caps: about 18,000 rows to read (the cap is "
                                     + "20,000) and 9,000 keys")
                .contains("2 workers, about")
                .contains("2 workers put 2 times the search load on the cluster")
                .contains("runAnyway=true");
        assertThat(streams.scanned()).isEmpty();
    }

    @Test
    void aJoinNearTheCapsIsAskedAboutInAChatAndRunsWhenTheUserSaysSo() {
        downloadsThenCopies(9_000, 9_000);
        config = config(1, 10, 1_000_000L, 20_000L);
        final Answering yes = new Answering(KeyStreamSequence.RUN);
        final Answering no = new Answering(KeyStreamSequence.DONT_RUN);

        assertThat(text(chat(no), downloadThenCopy("stream", null))).contains("The user chose not to run the join");
        assertThat(no.intro).contains("about 18,000 rows to read").contains("Run it?");
        final Map<String, Object> ran = structured(chat(yes), downloadThenCopy("stream", null));
        assertThat(ran).containsEntry("sequence_count", 9_000L);
    }

    @Test
    void theCapsStopTheJoinKeepingWhatItFound() {
        downloadsThenCopies(30_000, 30_000);
        config = config(1, 10, 1_000_000L, 25_000L);
        final Map<String, Object> args = new LinkedHashMap<>(downloadThenCopy("stream", null));
        args.put("runAnyway", true);

        final Map<String, Object> result = json(mcp(), args);

        assertThat((String) result.get("ended_early")).contains("cap of 25,000 rows read");
        assertThat(((Number) result.get("sequence_count")).longValue()).isPositive().isLessThan(30_000L);
        assertThat(streams.open()).isZero();
    }

    @Test
    void mismatchedJoinValuesAreDiagnosedWithTheTransformThatBridgesThem() {
        for (int i = 0; i < 5; i++) {
            proxy.add(proxyEvent("CORP\\" + user(i), "download", T0 + i * 60_000L, i));
            endpoint.add(endpointEvent(user(i), "copy", T0 + i * 60_000L + 5_000L, i));
        }

        final Map<String, Object> none = json(mcp(), downloadThenCopy("stream", null));

        assertThat(none).containsEntry("sequence_count", 0L);
        final List<String> diagnosis = (List<String>) none.get("diagnosis");
        assertThat(diagnosis.getFirst()).contains("step 1 (Proxy): 5 events, 5 keys")
                .contains("step 2 (Endpoint): 5 events, 5 keys");
        assertThat(diagnosis.get(1)).contains("No shared values between step 1 (Proxy: user) and step 2 "
                                              + "(Endpoint: UserName), e.g. `CORP\\" + user(0) + "` vs `"
                                              + user(0) + "`")
                .contains("With transform [strip_domain]");

        final Map<String, Object> args = downloadThenCopy("stream", null);
        final List<Map<String, Object>> steps = new ArrayList<>((List<Map<String, Object>>) args.get("steps"));
        final Map<String, Object> first = new LinkedHashMap<>(steps.getFirst());
        first.put("transform", List.of("strip_domain"));
        steps.set(0, first);
        final Map<String, Object> bridged = new LinkedHashMap<>(args);
        bridged.put("steps", steps);
        final Map<String, Object> found = json(mcp(), bridged);

        assertThat(found).containsEntry("sequence_count", 5L).doesNotContainKey("diagnosis");
        assertThat((List<Map<String, Object>>) found.get("steps")).first()
                .satisfies(step -> assertThat(step).containsEntry("transform", List.of("strip_domain")));
        assertThat(streams.scanned().getLast().keys().getFirst().transforms()).isEmpty();
        assertThat(streams.scanned().stream()
                .filter(side -> side.index().equals(PROXY))
                .reduce((a, b) -> b)
                .orElseThrow()
                .keys()
                .getFirst()
                .transforms()).containsExactly(Transform.STRIP_DOMAIN);
    }

    @Test
    void aBridgeIsFoundFromTheExamplesOrTheirShapes() {
        assertThat(KeyStreamSequence.bridge(List.of("CORP\\jsmith"), List.of("jsmith")))
                .containsExactly(Transform.STRIP_DOMAIN);
        // Not the same entities, but a domain on one side only.
        assertThat(KeyStreamSequence.bridge(List.of("CORP\\abc"), List.of("xyz")))
                .containsExactly(Transform.STRIP_DOMAIN);
        assertThat(KeyStreamSequence.bridge(List.of("JSmith@corp.example"), List.of("bob")))
                .containsExactly(Transform.STRIP_DOMAIN, Transform.LOWERCASE);
        assertThat(KeyStreamSequence.bridge(List.of("C:\\x\\report.docx"), List.of("report.docx")))
                .containsExactly(Transform.STRIP_DOMAIN);
        assertThat(KeyStreamSequence.bridge(List.of("/home/j/a.txt"), List.of("b.txt")))
                .containsExactly(Transform.FILE_NAME);
        assertThat(KeyStreamSequence.bridge(List.of("alice"), List.of("bob"))).isNull();
    }

    @Test
    void sidesOfAboutTheSameSizeArentPushedDown() {
        config = config(1, 50_000, 1_000_000L, 10_000_000L);
        downloadsThenCopies(100, 100);

        final Map<String, Object> result = json(mcp(), downloadThenCopy("stream", null));

        assertThat(result).containsEntry("sequence_count", 100L).doesNotContainKey("pushed_down");
        assertThat(((Number) result.get("rows_read")).longValue()).isEqualTo(200L);
    }

    @Test
    void keysThatBeganButNeverEndedAreCountedWithTheirSource() {
        for (int i = 0; i < 6; i++) {
            proxy.add(proxyEvent(user(i), "download", T0 + i * 60_000L, i));
        }
        for (int i = 0; i < 2; i++) {
            endpoint.add(endpointEvent(user(i), "copy", T0 + i * 60_000L + 5_000L, i));
        }

        final Map<String, Object> result = json(mcp(), downloadThenCopy("stream", null));

        assertThat(result).containsEntry("sequence_count", 2L);
        final Map<String, Object> quality = (Map<String, Object>) result.get("data_quality");
        assertThat(quality).containsEntry("opened_not_closed", 4L).containsEntry("opening_index", "Proxy")
                .containsEntry("closing_index", "Endpoint");
        assertThat((List<String>) quality.get("examples")).contains(user(2));
        assertThat((String) quality.get("note")).contains("Endpoint's source may be missing events");
    }

    @Test
    void whereTheLastStepIsPushedDownTheKeysThatNeverEndedAreEstimated() {
        config = config(1, 50_000, 1_000_000L, 10_000_000L);
        for (int i = 0; i < 20; i++) {
            proxy.add(proxyEvent(user(i), "download", T0 + i * 60_000L, i));
        }
        endpoint.add(endpointEvent(user(0), "copy", T0 + 5_000L, 0));

        final Map<String, Object> result = json(mcp(), downloadThenCopy("stream", null));

        assertThat((String) result.get("pushed_down")).contains("Step 2 has 1 keys");
        final Map<String, Object> quality = (Map<String, Object>) result.get("data_quality");
        assertThat(quality).containsEntry("opened_not_closed", 19L).containsEntry("estimated", true);
        assertThat((String) quality.get("note")).contains("Estimated: step 2 was pushed down");
    }

    @Test
    void aJoinEstimatedPastTheQueryTimeLimitRunsWithinItOverMcpSayingSo() {
        downloadsThenCopies(50, 50);
        inflateRows(3_000_000L);

        final Map<String, Object> result = json(mcp(), downloadThenCopy("stream", null));

        assertThat(result).containsEntry("time_limit_s", 120L).containsEntry("sequence_count", 50L);
        assertThat((String) result.get("time_note")).startsWith("Run within the query time limit (2 minutes)")
                .contains("runAnyway=true to run it up to the join time limit (15 minutes)");
    }

    @Test
    void runAnywayRunsUpToTheJoinTimeLimit() {
        downloadsThenCopies(50, 50);
        inflateRows(3_000_000L);
        final Map<String, Object> args = new LinkedHashMap<>(downloadThenCopy("stream", null));
        args.put("runAnyway", true);

        final Map<String, Object> result = json(mcp(), args);

        assertThat(result).containsEntry("time_limit_s", 900L);
        assertThat((String) result.get("time_note")).contains("as runAnyway asked");
        // A join that fits the query time limit keeps it.
        Mockito.doAnswer(invocation -> estimate(invocation.getArgument(0))).when(elasticEsql).query(Mockito.any());
        assertThat(json(mcp(), downloadThenCopy("stream", null))).doesNotContainKey("time_note");
    }

    @Test
    void inAChatTheUserChoosesTheJoinTimeLimitOrTheQueryTimeLimit() {
        downloadsThenCopies(50, 50);
        inflateRows(3_000_000L);
        final Answering longer = new Answering("Run it, for up to 15 minutes");
        final Answering within = new Answering("Run it within 2 minutes");

        assertThat(structured(chat(longer), downloadThenCopy("stream", null))).containsEntry("time_limit_s", 900L);
        assertThat(longer.intro).contains("longer than the query time limit (2 minutes)")
                .contains("it can run for up to 15 minutes");
        final Map<String, Object> ran = structured(chat(within), downloadThenCopy("stream", null));
        assertThat(ran).containsEntry("time_limit_s", 120L);
        assertThat((String) ran.get("time_note")).contains("as the user chose");
    }

    @Test
    void aJoinEstimatedPastEvenTheJoinTimeLimitSaysItWillLikelyEndEarly() {
        config = new AiWorkspaceConfig(null, null, null, null, null, null, StroomDuration.ofMinutes(2), null, null,
                null, null, null, null, null, 1, 0, 1_000_000L, 100_000_000L, StroomDuration.ofMinutes(3));
        downloadsThenCopies(50, 50);
        inflateRows(6_000_000L);
        final Map<String, Object> args = new LinkedHashMap<>(downloadThenCopy("stream", null));
        args.put("runAnyway", true);

        final Map<String, Object> result = json(mcp(), args);

        assertThat(result).containsEntry("time_limit_s", 180L);
        assertThat((String) result.get("time_note")).contains("longer than even the join time limit (3 minutes)")
                .contains("will likely end early");
    }

    @Test
    void autoRunsAsAStreamWhenTheDocsHaveDifferentTimeFields() {
        downloadsThenCopies(3, 3);
        Mockito.doThrow(new ElasticEql.Refusal(ElasticEql.Reason.DIFFERENT_TIME_FIELDS, "The Elastic Indexes have "
                                                                                     + "different time fields"))
                .when(elasticEql).target(Mockito.any());

        final Map<String, Object> result = json(mcp(), downloadThenCopy(null, null));

        assertThat(result).containsEntry("engine", "stream").containsEntry("sequence_count", 3L);
        assertThat((String) result.get("engine_reason")).isEqualTo(SequenceQueryTool.STREAM_TIME_FIELDS);
        // And engine=eql still refuses, saying what differs.
        assertThat(text(mcp(), downloadThenCopy("eql", null))).contains("different time fields");
    }

    @Test
    void autoRunsAsAStreamWhenTheFirstStepIsTooBigForEql() {
        downloadsThenCopies(3, 3);
        Mockito.doAnswer(invocation -> {
            final ElasticEsql.Request request = invocation.getArgument(0);
            return request.pipeline().contains("LIMIT " + (SequenceQueryTool.COUNT_CAP + 1))
                    ? result(List.of(List.of(SequenceQueryTool.COUNT_CAP + 1)), null)
                    : estimate(request);
        }).when(elasticEsql).query(Mockito.any());

        final Map<String, Object> result = json(mcp(), downloadThenCopy(null, null));

        assertThat(result).containsEntry("engine", "stream");
        assertThat((String) result.get("engine_reason")).startsWith("As one EQL sequence, step 1 matches more than "
                                                                    + "1000000 events")
                .contains("ran in Stroom as a streaming join instead");
        Mockito.verify(elasticEql, Mockito.never()).search(Mockito.any());
    }

    @Test
    void aDocTheUserCantUseIsRefused() {
        downloadsThenCopies(3, 3);
        streams.deny(ENDPOINT);

        assertThat(text(mcp(), downloadThenCopy("stream", null)))
                .contains("You may not use Elastic Index 'Endpoint' (endpoint-uuid), so the join can't read it.");
    }

    @Test
    void whatOnlyTheStreamCanDoIsRefusedForEqlAndALengthConditionForTheStream() {
        downloadsThenCopies(3, 3);
        final Map<String, Object> keysOnly = Map.of("engine", "eql", "steps", List.of(
                Map.of("index", "Users", "by", List.of("sAMAccountName"), "keysOnly", true),
                Map.of("index", "Proxy", "by", List.of("user"))));
        final Map<String, Object> length = Map.of("engine", "stream", "maxspan", "5m", "steps", List.of(
                Map.of("index", "Proxy", "by", List.of("user"), "where", List.of(Map.of("field", "url",
                        "condition", "GREATER_THAN", "value", "100", "length", true))),
                Map.of("index", "Endpoint", "by", List.of("UserName"))));

        assertThat(text(mcp(), keysOnly)).contains("keysOnly and transform need the stream engine");
        assertThat(text(mcp(), length)).contains("a condition on the length of 'url' can only run as EQL");
    }

    // --------------------------------------------------------------------------------

    // Each of `count` users downloads; the first `copies` of them then copy within a minute.
    private void downloadsThenCopies(final int count, final int copies) {
        for (int i = 0; i < count; i++) {
            proxy.add(proxyEvent(user(i), "download", T0 + i * 1_000L, i));
            if (i < copies) {
                endpoint.add(endpointEvent(user(i), "copy", T0 + i * 1_000L + 30_000L, i));
            }
        }
    }

    private static String user(final int i) {
        return String.format("user%06d", i);
    }

    private static Map<String, Object> proxyEvent(final String user, final String action, final long time,
                                                  final long id) {
        final Map<String, Object> event = new HashMap<>();
        event.put("@timestamp", time);
        event.put("user", user);
        event.put("action", action);
        event.put("StreamId", 1L);
        event.put("EventId", id + 1);
        return event;
    }

    private static Map<String, Object> endpointEvent(final String user, final String action, final long time,
                                                     final long id) {
        final Map<String, Object> event = new HashMap<>();
        event.put("EventTime", time);
        event.put("UserName", user);
        event.put("action", action);
        event.put("StreamId", 2L);
        event.put("EventId", id + 1);
        return event;
    }

    private static Map<String, Object> downloadThenCopy(final String engine, final Integer limit) {
        final Map<String, Object> args = new LinkedHashMap<>();
        if (engine != null) {
            args.put("engine", engine);
        }
        args.put("maxspan", "2m");
        args.put("steps", List.of(
                Map.of("index", "Proxy", "by", List.of("user"), "where", List.of(Map.of("field", "action",
                        "value", "download"))),
                Map.of("index", "Endpoint", "by", List.of("UserName"), "where", List.of(Map.of("field", "action",
                        "value", "copy")))));
        if (limit != null) {
            args.put("limit", limit);
        }
        return args;
    }

    // The estimate's event counts as `rows`, its keys as the in-memory events have them.
    private void inflateRows(final long rows) {
        Mockito.doAnswer(invocation -> {
            final ElasticEsql.Request request = invocation.getArgument(0);
            return request.pipeline().contains(" BY ")
                    ? estimate(request)
                    : result(List.of(List.of(rows)), null);
        }).when(elasticEsql).query(Mockito.any());
    }

    // An ES|QL estimate from the in-memory events: a count, or the commonest keys with how many there are.
    private ElasticEsql.Result estimate(final ElasticEsql.Request request) {
        final List<Map<String, Object>> events = streams.events(request.index()).stream()
                .filter(event -> where(request.pipeline(), event))
                .toList();
        final Matcher by = BY.matcher(request.pipeline());
        if (!by.find()) {
            return result(List.of(List.of((long) events.size())), null);
        }
        final List<String> fields = List.of(by.group(1).split(",\\s*"));
        final Map<List<Object>, Long> counts = events.stream()
                .collect(Collectors.groupingBy(event -> fields.stream().map(event::get).toList(),
                        LinkedHashMap::new, Collectors.counting()));
        final List<List<Object>> rows = counts.entrySet().stream()
                .sorted(Map.Entry.<List<Object>, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(KeyStreamSequence.EXAMPLES)
                .map(entry -> {
                    final List<Object> row = new ArrayList<>();
                    row.add(entry.getValue());
                    row.addAll(entry.getKey());
                    return row;
                })
                .toList();
        return result(rows, counts.size() > rows.size()
                ? (long) counts.size()
                : null);
    }

    // The steps' one condition, action == "x", as the pipeline has it.
    private static boolean where(final String pipeline, final Map<String, Object> event) {
        final Matcher action = Pattern.compile("action == \"([^\"]+)\"").matcher(pipeline);
        return !action.find() || action.group(1).equals(event.get("action"));
    }

    private static ElasticEsql.Result result(final List<List<Object>> rows, final Long total) {
        return new ElasticEsql.Result("FROM x", "x", List.of(new ElasticEsql.Column("n", "long")), rows, 1,
                rows.size(), null, total != null, true, total, null, "9.5.3", List.of());
    }

    private static AiWorkspaceConfig config(final int workers,
                                            final int pushDownKeys,
                                            final Long maxKeys,
                                            final Long maxRows) {
        return new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, workers, pushDownKeys, maxKeys, maxRows);
    }

    private Map<String, Object> json(final ToolCallContext context, final Map<String, Object> args) {
        final String text = text(context, args);
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
            return list.stream().map(TestKeyStreamSequence::longs).toList();
        }
        return value;
    }

    private Map<String, Object> structured(final ToolCallContext context, final Map<String, Object> args) {
        final ToolResult result = tool.execute(new ToolCall("call-1", SequenceQueryTool.NAME,
                JsonUtil.writeValueAsString(args)), context);
        assertThat(result.structuredContent()).as(result.text()).isNotNull();
        return (Map<String, Object>) longs(JsonUtil.readValue(JsonUtil.writeValueAsString(result.structuredContent()),
                Map.class));
    }

    private String text(final ToolCallContext context, final Map<String, Object> args) {
        return tool.execute(new ToolCall("call-1", SequenceQueryTool.NAME, JsonUtil.writeValueAsString(args)),
                context).text();
    }

    private ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(() -> config))
                .build();
    }

    private ToolCallContext chat(final UserQuestions questions) {
        return ToolCallContext.builder(ToolSession.chat(1))
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .userQuestions(questions)
                .limits(SessionLimits.lookedUp(() -> config))
                .build();
    }


    // --------------------------------------------------------------------------------


    // Answers the join's question with one of its choices.
    private static final class Answering implements UserQuestions {

        private final String answer;
        private String intro;

        private Answering(final String answer) {
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
            this.intro = intro;
            return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of(KeyStreamSequence.RUN_FORM, answer));
        }
    }
}
