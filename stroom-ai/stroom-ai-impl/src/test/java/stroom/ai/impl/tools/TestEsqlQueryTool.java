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
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(request.timeRange().getFrom()).isEqualTo("day() - 7d");
            assertThat(request.timeRange().getTo()).isEqualTo("now()");
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
                          + "they can differ from Stroom searches (run_stroomql) and dashboards");
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
        return new Result("FROM i", "i", List.of(new Column("n", "long")), rows, 1, limit, null, truncated,
                grouped, total, null, "9.5.3");
    }

    private static Request request(final BuiltInToolWorld world) {
        final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        final ElasticEsql esql = world.elasticEsql();
        Mockito.verify(esql).query(captor.capture());
        return captor.getValue();
    }
}
