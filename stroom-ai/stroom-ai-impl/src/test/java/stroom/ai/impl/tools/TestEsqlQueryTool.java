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

import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticEsql.Column;
import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;
import stroom.ai.api.ElasticEsql.Request;
import stroom.ai.api.ElasticEsql.Result;
import stroom.ai.impl.BuiltInToolWorld;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEsqlQueryTool {

    private static final String GROUPED = "| STATS n = COUNT(*) BY UserId | SORT n DESC | LIMIT 2";

    @TempDir
    Path tempDir;

    @Test
    void aGroupedQueryCutShortSaysHowManyGroupsThereAre() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED)));

            assertThat(output)
                    .contains("FROM test-events\n| STATS n = COUNT(*) BY UserId")
                    .contains("| n | UserId |")
                    .contains("| 5 | alice |")
                    .contains("| 3 | bob |")
                    .contains("2 groups.")
                    .contains("there are 3 groups in all, so these are the first 2 of 3 groups in the query's "
                              + "order, not all of them")
                    .contains("The rows are in /outputs/esql/");
            final Request request = request(world);
            assertThat(request.index()).isEqualTo(BuiltInToolWorld.ELASTIC_INDEX);
            assertThat(request.pipeline()).isEqualTo(GROUPED);
            assertThat(request.filter()).isNull();
            assertThat(request.timeRange()).isNull();
            assertThat(request.countTotal()).isTrue();
        }
    }

    @Test
    void conditionsAndTimesAreTheFilter() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED, "limit", 20,
                    "where", List.of(Map.of("field", "UserId", "value", "alice*")),
                    "timeFrom", "day() - 7d", "timeTo", "now()")));

            final Request request = request(world);
            assertThat(request.limit()).isEqualTo(20);
            assertThat(request.filter().getChildren()).singleElement()
                    .isInstanceOfSatisfying(ExpressionTerm.class, term -> {
                        assertThat(term.getField()).isEqualTo("UserId");
                        assertThat(term.getCondition()).isEqualTo(Condition.EQUALS);
                        assertThat(term.getValue()).isEqualTo("alice*");
                    });
            // Resolved in the call's zone (UTC here) to the times they stood for, so the result can say them.
            assertThat(request.timeRange().getFrom()).isEqualTo(
                    LocalDate.now(ZoneOffset.UTC).minusDays(7) + "T00:00:00Z");
            assertThat(request.timeRange().getTo()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        }
    }

    @Test
    void theTimeRangeAndTheRowsTimesAreInTheZoneGiven() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any())).thenReturn(result(
                    List.of(List.of("2026-01-01T01:00:00.000Z")), 10, false, false, null));
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", "| KEEP t", "timeFrom", "2026-01-01T12:00:00",
                    "timeTo", "2026-01-01T13:00:00Z", "timeZone", "+11:00")));

            final Request request = request(world);
            assertThat(request.timeRange().getFrom()).isEqualTo("2026-01-01T12:00:00+11:00");
            assertThat(request.timeRange().getTo()).isEqualTo("2026-01-01T13:00:00Z");
            // The pipeline's own date work is asked for in the zone; this cluster refused it (no zone in the result).
            assertThat(request.timeZone()).isEqualTo("+11:00");
            assertThat(output).contains("| 2026-01-01T12:00:00.000+11:00 |")
                    .contains("Times are in +11:00. " + EsqlQueryTool.ESQL_UTC_NOTE);
        }
    }

    @Test
    void aPipelineRunInTheZoneSaysNothingOfUtc() throws Exception {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any())).thenReturn(new Result("FROM i", "i",
                    List.of(new Column("d", "date")), List.of(List.of("2026-01-01T00:00:00.000+11:00")), 1, 10,
                    null, false, false, null, null, "9.5.3", List.of(), "Australia/Canberra"));
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", "| STATS BY d = DATE_TRUNC(1 day, t)",
                    "timeZone", "Australia/Canberra")));

            assertThat(request(world).timeZone()).isEqualTo("Australia/Canberra");
            assertThat(output).contains("| 2026-01-01T00:00:00.000+11:00 |")
                    .contains("Times are in Australia/Canberra (+1").doesNotContain(EsqlQueryTool.ESQL_UTC_NOTE);
        }
    }

    @Test
    void aPipelineInUtcAsksForNoZone() throws Exception {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any())).thenReturn(result(List.of(), 10, false, false,
                    null));
            world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of("index", "elastic-index-uuid",
                    "pipeline", "| KEEP t", "timeZone", "UTC")));

            assertThat(request(world).timeZone()).isNull();
        }
    }

    @Test
    void anUnknownZoneIsRefused() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED, "timeZone", "Mars")));

            assertThat(output).isEqualTo("Error: " + UserTimeZones.unknown("Mars"));
            Mockito.verifyNoInteractions(world.elasticEsql());
        }
    }

    @Test
    void aConditionOnAFieldTheIndexHasntIsRefusedWithItsFields() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED,
                    "where", List.of(Map.of("field", "UserID", "value", "alice")))));

            assertThat(output).contains("Not fields of index").contains("UserID (like UserId)")
                    .contains("describe_data_source");
            Mockito.verifyNoInteractions(world.elasticEsql());
        }
    }

    @Test
    void aLuceneIndexIsRefusedForRunStroomQl() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "index-uuid", "pipeline", GROUPED)));

            assertThat(output).contains("isn't an Elastic Index").contains("Use run_stroomql for a Lucene index");
            Mockito.verifyNoInteractions(world.elasticEsql());
        }
    }

    @Test
    void anIndexTheUserCantUseIsntFound() {
        try (BuiltInToolWorld world = new BuiltInToolWorld(tempDir, Set.of(), Set.of(),
                false)) {
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED)));

            assertThat(output).contains("No Elastic Index doc with uuid or name 'elastic-index-uuid' that you "
                                        + "can use");
            Mockito.verifyNoInteractions(world.elasticEsql());
        }
    }

    @Test
    void refusalsSayWhatToDoInstead() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any()))
                    .thenThrow(new Refusal(Reason.COMMAND_NOT_ALLOWED, "The ES|QL command FROM isn't allowed here"));

            assertThat(world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", "| FROM other"))))
                    .contains("Error: The ES|QL command FROM isn't allowed here.");
        }
    }

    @Test
    void aPermissionRefusedWhenTheQueryRunsIsNotFound() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any()))
                    .thenThrow(new PermissionException(null, "You are not authorised"));

            assertThat(world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED))))
                    .contains(DataSourceAccess.NOT_FOUND);
        }
    }

    @Test
    void theQueryIsAudited() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED,
                    "where", List.of(Map.of("field", "UserId", "value", "alice")))));

            Mockito.verify(world.auditLog()).searched(Mockito.any(), Mockito.eq(BuiltInToolWorld.ELASTIC_INDEX),
                    Mockito.argThat(query -> query.startsWith("FROM test-events")
                                             && query.contains("filter: UserId EQUALS alice")),
                    Mockito.eq(2L), Mockito.eq("ES|QL query by the esql_query tool"));
        }
    }

    @Test
    void refusalMessages() {
        final List<QueryField> fields = List.of(QueryField.createText("user.name"),
                QueryField.createText("url.domain"));

        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.UNKNOWN_FIELD, "Elasticsearch refused the query: line "
                                                                           + "2:28: Unknown column [user.nme]",
                List.of("user.nme")), fields))
                .contains("Not fields of the index: user.nme (like user.name)")
                .contains("describe_data_source gives the index's fields; they include: url.domain, user.name.");
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.TEXT_GROUPING, "'message' is a text field: group by "
                                                                           + "its keyword sub-field "
                                                                           + "'message.keyword' instead",
                List.of("message", "message.keyword")), fields))
                .contains("group by its keyword sub-field 'message.keyword' instead");
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.CLUSTER_TOO_OLD, "The cluster is Elasticsearch "
                                                                             + "8.11.0: ES|QL needs 8.14 or "
                                                                             + "later"), fields))
                .contains("Use run_stroomql instead");
    }

    @Test
    void refusalsEndWithOneFullStop() {
        final List<QueryField> fields = List.of(QueryField.createText("event.code"));

        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.COMMAND_NOT_ALLOWED, "The ES|QL command MV isn't "
                                                                                 + "allowed here. Only these "
                                                                                 + "commands are allowed."), fields))
                .endsWith("are allowed.")
                .doesNotContain("..");
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.UNKNOWN_FIELD, "Elasticsearch refused the query: line "
                                                                           + "3:62: Unknown column [event.action], "
                                                                           + "did you mean [event.code]?",
                List.of("event.action")), fields))
                .contains("did you mean [event.code]? Not fields of the index: event.action")
                .doesNotContain("?.");
    }

    @Test
    void aBackslashInAStringIsExplained() {
        // As Elasticsearch 9.5.3 refuses '"a\.example"'.
        final String refused = EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: "
                                                                                 + "line 2:113: token recognition "
                                                                                 + "error at: '\".*(partner|"
                                                                                 + "fabrikam)\\.'"), List.of());

        assertThat(refused).contains(EsqlQueryTool.BACKSLASH_HINT);
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 5:51: "
                                                                     + "token recognition error at: '='"), List.of()))
                .doesNotContain(EsqlQueryTool.BACKSLASH_HINT);
    }

    @Test
    void aReservedWordAsAColumnNameIsExplained() {
        // As Elasticsearch 9.5.3 refuses '| STATS first = MIN(n) BY user'.
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 2:9: "
                                                                     + "no viable alternative at input 'first ='"),
                List.of()))
                .contains("If 'first' is meant as a column's name: it is a reserved word in ES|QL, so rename the "
                          + "column (e.g. first_seen) or put it in backquotes (`first`).");
        // And '| EVAL by = 1', in its other form.
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 2:7: "
                                                                     + "mismatched input 'by' expecting "
                                                                     + "{QUOTED_STRING, INTEGER_LITERAL}"),
                List.of()))
                .contains("If 'by' is meant as a column's name: it is a reserved word");
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 2:7: "
                                                                     + "no viable alternative at input 'user ='"),
                List.of())).doesNotContain("reserved word");
    }

    @Test
    void ifIsExplainedAsCase() {
        // As Elasticsearch 9.5.3 refuses '| EVAL x = IF(n > 1, 1, 0)'.
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 2:11: "
                                                                     + "Unknown function [IF]"), List.of()))
                .endsWith(EsqlQueryTool.IF_HINT);
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 2:11: "
                                                                     + "Unknown function [iif]"), List.of()))
                .endsWith(EsqlQueryTool.IF_HINT);
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.REFUSED, "Elasticsearch refused the query: line 2:11: "
                                                                     + "Unknown function [iff2]"), List.of()))
                .doesNotContain(EsqlQueryTool.IF_HINT);
    }

    @Test
    void anUnescapedAtInRlikeIsNoted() {
        // On Elasticsearch 9.5.3 'RLIKE "C@"' matched "Copy": @ is any string. "a\\@b", """a\@b""" and "a[@]b"
        // matched "a@b" only.
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE email RLIKE \".*@example\\\\.com\"")).isTrue();
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE email rlike \"\"\".*@x\"\"\"")).isTrue();
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE email RLIKE \".*\\\\@example\\\\.com\"")).isFalse();
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE email RLIKE \"\"\".*\\@x\"\"\"")).isFalse();
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE email RLIKE \"a[@]b\"")).isFalse();
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE email LIKE \"*@example.com\"")).isFalse();
        assertThat(EsqlQueryTool.unescapedAtInRlike("| WHERE @timestamp > \"2026\" AND x RLIKE \"a.*\"")).isFalse();

        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any())).thenReturn(new Result(
                    "FROM i\n| WHERE email RLIKE \".*@x\\\\.com\"", "i", List.of(new Column("n", "long")),
                    List.of(), 1, 1000, null, false, false, null, null, "9.5.3", List.of()));

            assertThat(world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", "| WHERE email RLIKE \".*@x\\\\.com\""))))
                    .contains(EsqlQueryTool.RLIKE_AT_NOTE);
        }
        assertThat(new EsqlQueryTool(null, null, null, null, null).definition().description())
                .contains("In RLIKE, @ matches any string: escape it, or use LIKE for simple patterns.")
                .contains("ES|QL has no IF: use CASE(condition, then, else).")
                .contains("COUNT_DISTINCT(target) BY source")
                .contains("MIN(@timestamp) BY user, value");
    }

    @Test
    void theDefaultLimitIsTheMostRowsAndACutAtItSaysToSplitByTime() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", "| KEEP UserId")));

            assertThat(request(world).limit()).isEqualTo(ElasticEsql.MAX_ROWS);
        }
        assertThat(EsqlQueryTool.rowsNote(result(List.of(List.of(1L)), ElasticEsql.MAX_ROWS, true, false, 25_000L)))
                .contains("there are 25000 rows in all.")
                .contains(EsqlQueryTool.MAX_ROWS_NOTE);
        assertThat(EsqlQueryTool.rowsNote(result(List.of(List.of(1L)), 100, true, false, 25_000L)))
                .doesNotContain(EsqlQueryTool.MAX_ROWS_NOTE);
    }

    @Test
    void warningsAreShownWithTheMultiValueHint() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any())).thenReturn(result(List.of(), 1000, false,
                    false, null, List.of("Line 2:9: evaluation of [t == \"a@x\"] failed, treating result as null. "
                                         + "Only first 20 failures recorded.",
                            "Line 2:9: java.lang.IllegalArgumentException: single-value function encountered "
                            + "multi-value")));

            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", "| WHERE t == \"a@x\"")));

            assertThat(output)
                    .contains("Elasticsearch warned:\n- Line 2:9: evaluation of [t == \"a@x\"] failed")
                    .contains("- Line 2:9: java.lang.IllegalArgumentException: single-value function")
                    .contains(EsqlQueryTool.MULTI_VALUE_HINT)
                    .contains("No rows.");
        }
    }

    @Test
    void otherWarningsAreShownWithoutTheHint() {
        final String note = EsqlQueryTool.warningsNote(result(List.of(), 10, false, false, null,
                List.of("Line 2:3: something else")));

        assertThat(note).isEqualTo("Elasticsearch warned:\n- Line 2:3: something else");
        assertThat(EsqlQueryTool.warningsNote(result(List.of(), 10, false, false, null))).isEmpty();
    }

    @Test
    void everyResultSaysItsCountsMayIncludeStreamsTheUserCantSee() {
        // The documents' streams aren't looked up (for speed, the user's choice): the description says so, and the
        // result carries no note of it.
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final String output = world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED)));

            assertThat(output).doesNotContain("streams the user can't see");
        }
        assertThat(new EsqlQueryTool(null, null, null, null, null).definition().description())
                .contains("may include documents from streams the user can't see or that have been deleted, so "
                          + "they can differ from Stroom searches (run_stroomql) and dashboards: when the user "
                          + "needs figures that match those exactly, use a Stroom search (run_stroomql).");
    }

    @Test
    void theRunsStopReachesTheQueryAndAStoppedQueryEndsTheRunStopped() {
        // Before, a stopped query came back as the tool's error ("Error running built-in tool ..."), after the
        // query had run to its end or time limit.
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            Mockito.when(world.elasticEsql().query(Mockito.any())).thenThrow(new Refusal(Reason.STOPPED,
                    "The query was stopped, so it was cancelled in Elasticsearch"));

            assertThatThrownBy(() -> world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED)), () -> true))
                    .isInstanceOf(TaskTerminatedException.class);
            assertThat(request(world).stopped().getAsBoolean()).isTrue();
        }
    }

    @Test
    void aQueryOutsideAStoppedRunIsNeverStopped() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            world.run(EsqlQueryTool.NAME, BuiltInToolWorld.json(Map.of(
                    "index", "elastic-index-uuid", "pipeline", GROUPED)));

            assertThat(request(world).stopped().getAsBoolean()).isFalse();
        }
    }

    @Test
    void busyAndTooMuchMemoryRefusalsKeepTheirAdvice() {
        final String busy = "Elasticsearch is short of memory right now, so it refused the query: try again shortly. "
                            + "Elasticsearch said: [parent] Data too large";
        final String tooBig = "The query needs more memory than Elasticsearch allows it: narrow the time range, group "
                              + "by fewer high-cardinality fields, avoid MV_EXPAND on large multi-value fields, or "
                              + "lower the LIMIT. Elasticsearch said: [request] Data too large";

        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.CLUSTER_BUSY, busy), List.of())).isEqualTo(busy + ".");
        assertThat(EsqlQueryTool.refusal(new Refusal(Reason.TOO_MUCH_MEMORY, tooBig), List.of()))
                .isEqualTo(tooBig + ".");
    }

    @Test
    void theDescriptionSteersOrderedSequencesToEqlSequence() {
        // Seen in acceptance runs on a small model: "a clipboard copy, then PowerShell within 2 minutes" answered by
        // joining STATS results by hand.
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            assertThat(world.builtInTools().toolMap().keySet().stream()
                    .filter(specification -> EsqlQueryTool.NAME.equals(specification.name()))
                    .findFirst()
                    .orElseThrow()
                    .description())
                    .contains("Not for ordered sequences (X then Y within N, in one doc or across several): "
                              + "joins of STATS results get them wrong; use sequence_query.");
        }
    }

    @Test
    void describeDataSourceSteersAggregationsToEsqlQuery() {
        assertThat(DescribeDataSourceTool.esqlNote("9.5.3", List.of(QueryField.createKeyword("user.name"),
                QueryField.createLong("bytes"))))
                .isEqualTo("Aggregations on this index (Elasticsearch 9.5.3): use esql_query, which Elasticsearch "
                           + "answers itself, e.g. pipeline '| STATS n = COUNT(*), total = SUM(bytes) BY user.name "
                           + "| SORT total DESC | LIMIT 10'.\n");
    }

    @Test
    void theTableToolSaysWhenEsqlQueryWouldHaveGrouped() {
        final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
        Mockito.when(dataSourceAccess.find("elastic-index-uuid")).thenReturn(List.of(
                BuiltInToolWorld.ELASTIC_INDEX));
        Mockito.when(dataSourceAccess.find("index-uuid")).thenReturn(List.of(BuiltInToolWorld.INDEX));
        final TableTool tool = new TableTool(Mockito.mock(ChatWorkspace.class),
                Mockito.mock(ColumnSummariser.class));
        tool.setDataSourceAccess(dataSourceAccess);
        final TableEngine.Query grouped = new TableEngine.Query(List.of(), List.of(), List.of(), List.of("UserId"),
                List.of(), false, false, List.of(), null);
        final TableEngine.Query rows = new TableEngine.Query(List.of(), List.of(), List.of(), List.of(), List.of(),
                false, false, List.of(), null);

        assertThat(tool.esqlNote("elastic-index-uuid", grouped, 5_000))
                .contains("These 5000 rows were read from ElasticIndex 'Test elastic index'")
                .contains("esql_query would have done that in Elasticsearch");
        // Few rows, rows not grouped, or not an Elastic Index: nothing to say.
        assertThat(tool.esqlNote("elastic-index-uuid", grouped, 50)).isEmpty();
        assertThat(tool.esqlNote("elastic-index-uuid", rows, 5_000)).isEmpty();
        assertThat(tool.esqlNote("index-uuid", grouped, 5_000)).isEmpty();
        assertThat(tool.esqlNote(null, grouped, 5_000)).isEmpty();
    }

    @Test
    void rowsNotes() {
        assertThat(EsqlQueryTool.rowsNote(result(List.of(List.of(1L)), 1000, false, false, null)))
                .isEqualTo("1 row.");
        assertThat(EsqlQueryTool.rowsNote(result(List.of(List.of(1L), List.of(2L)), 2, true, false, 40L)))
                .isEqualTo("2 rows. They reached the limit (2), so they are cut short: there are 40 rows in all.");
        assertThat(EsqlQueryTool.rowsNote(result(List.of(List.of(1L), List.of(2L)), 2, true, true, null)))
                .contains("how many there are in all isn't known")
                .contains("don't present them as complete");
    }

    private static Result result(final List<List<Object>> rows,
                                 final int limit,
                                 final boolean truncated,
                                 final boolean grouped,
                                 final Long total) {
        return result(rows, limit, truncated, grouped, total, List.of());
    }

    private static Result result(final List<List<Object>> rows,
                                 final int limit,
                                 final boolean truncated,
                                 final boolean grouped,
                                 final Long total,
                                 final List<String> warnings) {
        return new Result("FROM i", "i", List.of(new Column("n", "long")), rows, 1, limit, null, truncated,
                grouped, total, null, "9.5.3", warnings);
    }

    private static Request request(final BuiltInToolWorld world) {
        final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        final ElasticEsql esql = world.elasticEsql();
        Mockito.verify(esql).query(captor.capture());
        return captor.getValue();
    }
}
