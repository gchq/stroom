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

package stroom.ai.impl.workspace;

import stroom.ai.impl.workspace.TableEngine.Aggregate;
import stroom.ai.impl.workspace.TableEngine.Condition;
import stroom.ai.impl.workspace.TableEngine.Eval;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Order;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTableEngine {

    @TempDir
    Path tempDir;

    private Path csv;
    private Path markdown;

    @BeforeEach
    void setUp() throws IOException {
        csv = tempDir.resolve("events.csv");
        Files.writeString(csv, """
                host,bytes,message
                fw1,100,Login failed for 10.0.0.1
                fw2,50,"Login failed for 10.0.0.2, again"
                fw1,25,Disk 91% full
                fw3,7,Login failed for 10.0.0.9
                fw1,,Started
                """);
        markdown = tempDir.resolve("12.md");
        Files.writeString(markdown, """
                | Host | Count |
                | --- | --- |
                | fw1 | 10 |
                | fw2 | 9 |
                | fw1 | 2 |
                """);
    }

    @Test
    void testFilterSelectAndSort() {
        final Result result = TableEngine.run(csv, Format.CSV, query()
                .where(List.of(new Condition("bytes", ">", "20")))
                .select(List.of("host", "bytes"))
                .orderBy(List.of(new Order("bytes", false)))
                .build());

        assertThat(result.columns()).containsExactly("host", "bytes");
        // Sorted as numbers, not text.
        assertThat(result.rows()).containsExactly(List.of("fw1", "25"), List.of("fw2", "50"),
                List.of("fw1", "100"));
        assertThat(result.rowsRead()).isEqualTo(5);
        assertThat(result.rowsMatched()).isEqualTo(3);
    }

    @Test
    void testConditions() {
        assertThat(count(new Condition("host", "=", "fw1"))).isEqualTo(3);
        assertThat(count(new Condition("host", "!=", "fw1"))).isEqualTo(2);
        assertThat(count(new Condition("message", "contains", "LOGIN"))).isEqualTo(3);
        assertThat(count(new Condition("message", "not_contains", "login"))).isEqualTo(2);
        assertThat(count(new Condition("message", "starts_with", "Disk"))).isEqualTo(1);
        assertThat(count(new Condition("message", "ends_with", "again"))).isEqualTo(1);
        assertThat(count(new Condition("message", "matches", "10\\.0\\.0\\.[12]$"))).isEqualTo(1);
        assertThat(count(new Condition("bytes", "is_empty", null))).isEqualTo(1);
        assertThat(count(new Condition("bytes", "not_empty", null))).isEqualTo(4);
        assertThat(count(new Condition("bytes", "<=", "50"))).isEqualTo(3);
        assertThatThrownBy(() -> count(new Condition("host", "like", "x"))).hasMessageContaining("is not a condition");
        assertThatThrownBy(() -> count(new Condition("nope", "=", "x")))
                .hasMessageContaining("There is no column 'nope'. The columns are: host, bytes, message.");
        assertThatThrownBy(() -> count(new Condition("host", "=", null))).hasMessageContaining("Give a value");
    }

    @Test
    void testGroupAndAggregate() {
        final Result result = TableEngine.run(csv, Format.CSV, query()
                .groupBy(List.of("host"))
                .aggregates(List.of(new Aggregate("count", null, null), new Aggregate("sum", "bytes", "total"),
                        new Aggregate("avg", "bytes", null), new Aggregate("max", "bytes", null),
                        new Aggregate("count_distinct", "message", null)))
                .orderBy(List.of(new Order("total", true)))
                .build());

        assertThat(result.columns()).containsExactly("host", "count", "total", "avg(bytes)", "max(bytes)",
                "count_distinct(message)");
        assertThat(result.rows()).containsExactly(
                List.of("fw1", "3", "125", "62.5", "100", "3"),
                List.of("fw2", "1", "50", "50", "50", "1"),
                List.of("fw3", "1", "7", "7", "7", "1"));
    }

    @Test
    void testDistinctWithMasking() {
        final Result result = TableEngine.run(csv, Format.CSV, query()
                .select(List.of("message"))
                .distinct(true)
                .mask(true)
                .orderBy(List.of(new Order("count", true)))
                .build());

        assertThat(result.rows()).first().isEqualTo(List.of("Login failed for <ip>", "2"));
        assertThat(result.rows()).contains(List.of("Disk <n>% full", "1"));
    }

    @Test
    void testMarkdownTables() {
        final Result result = TableEngine.run(markdown, Format.MARKDOWN, query()
                .groupBy(List.of("host"))
                .aggregates(List.of(new Aggregate("sum", "Count", null)))
                .build());

        // Column names match ignoring case.
        assertThat(result.rows()).containsExactly(List.of("fw1", "12"), List.of("fw2", "9"));
    }

    @Test
    void testLimit() {
        assertThat(TableEngine.run(csv, Format.CSV, query().limit(2).build()).rows()).hasSize(2);
        assertThat(TableEngine.run(csv, Format.CSV, query().limit(2).build()).rowsMatched()).isEqualTo(5);
    }

    @Test
    void testReDoSInAConditionIsStopped() throws IOException {
        final Path evil = tempDir.resolve("evil.csv");
        Files.writeString(evil, "a\n\"" + "a,".repeat(40) + "\"\n");

        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(200), () -> TableEngine.run(evil, Format.CSV,
                query().where(List.of(new Condition("a", "matches", "(.*?,){25}x"))).build())))
                .isInstanceOf(ToolTimeoutException.class);
    }

    @Test
    void testComputedColumnsCanBeFilteredGroupedAndSorted() {
        final Result result = TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("kb", "${bytes} / 1000"),
                        // One computed column can use another.
                        new Eval("site", "upperCase(substring(${host}, 0, 2))"),
                        new Eval("big", "if(${kb} >= 0.05, 'yes', 'no')")))
                .where(List.of(new Condition("big", "=", "yes")))
                .select(List.of("host", "kb", "site", "big"))
                .orderBy(List.of(new Order("kb", true)))
                .build());

        assertThat(result.columns()).containsExactly("host", "kb", "site", "big");
        assertThat(result.rows()).containsExactly(List.of("fw1", "0.1", "FW", "yes"),
                List.of("fw2", "0.05", "FW", "yes"));
    }

    @Test
    void testComputedColumnsWithStringDateAndMissingValues() throws IOException {
        final Path times = tempDir.resolve("times.csv");
        Files.writeString(times, """
                time,user,score
                2026-10-18T09:15:00.000Z,Alice,3
                2026-10-18T09:45:30.000Z,bob,abc
                ,carol,
                """);
        final Result result = TableEngine.run(times, Format.CSV, query()
                .eval(List.of(new Eval("hour", "formatDate(floorHour(${time}), 'HH:mm')"),
                        new Eval("name", "concat(upperCase(${user}), '!')"),
                        new Eval("double", "${score} * 2"),
                        new Eval("len", "stringLength(${user})")))
                .select(List.of("hour", "name", "double", "len"))
                .build());

        assertThat(result.rows()).containsExactly(
                List.of("09:00", "ALICE!", "6", "5"),
                // A value that isn't a number gives an error, in its cell.
                List.of("09:00", "BOB!", result.rows().get(1).get(2), "3"),
                // An empty cell is no value.
                List.of("", "CAROL!", "", "5"));
        assertThat(result.rows().get(1).get(2)).satisfiesAnyOf(
                value -> assertThat(value).isEmpty(),
                value -> assertThat(value).startsWith("ERR"));
    }

    @Test
    void testExpressionAggregates() {
        final Result result = TableEngine.run(csv, Format.CSV, query()
                .groupBy(List.of("host"))
                .aggregates(List.of(new Aggregate("count", null, null),
                        new Aggregate(null, null, "mean", "round(sum(${bytes}) / count(), 1)"),
                        new Aggregate(null, null, null, "max(${bytes}) - min(${bytes})")))
                .orderBy(List.of(new Order("host", false)))
                .build());

        assertThat(result.columns()).containsExactly("host", "count", "mean", "max(${bytes}) - min(${bytes})");
        // As in Stroom, min takes fw1's empty cell as the least value, so the difference is no value.
        assertThat(result.rows()).containsExactly(
                List.of("fw1", "3", "41.7", ""),
                List.of("fw2", "1", "50", "0"),
                List.of("fw3", "1", "7", "0"));

        final Result filtered = TableEngine.run(csv, Format.CSV, query()
                .where(List.of(new Condition("bytes", "not_empty", null)))
                .groupBy(List.of("host"))
                .aggregates(List.of(new Aggregate(null, null, "range", "max(${bytes}) - min(${bytes})")))
                .limit(1)
                .build());
        assertThat(filtered.rows()).containsExactly(List.of("fw1", "75"));
    }

    @Test
    void testExpressionErrors() {
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("x", "uppercase(${nope})"))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("'nope'")
                .hasMessageContaining("host, bytes, message");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("x", "substrin(${host}, 0, 1)"))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("substrin")
                .hasMessageContaining("Did you mean substring");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .groupBy(List.of("host"))
                .aggregates(List.of(new Aggregate(null, null, null, "avg(${bytes})"))).build()))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("Did you mean average?");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("x", "sum(${bytes})"))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("aggregates");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("host", "${bytes}"))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("already a column");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("x", "askAi(${message})"))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("isn't available");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .groupBy(List.of("host"))
                .aggregates(List.of(new Aggregate(null, null, null, "first(${bytes})"))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("isn't available");
        assertThatThrownBy(() -> TableEngine.run(csv, Format.CSV, query()
                .eval(List.of(new Eval("x", "(${bytes} + "))).build()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("can't be read");
    }

    @Test
    void testCompareValues() {
        assertThat(TableEngine.compareValues("9", "10")).isNegative();
        assertThat(TableEngine.compareValues("b", "a")).isPositive();
        assertThat(TableEngine.compareValues("1", "a")).isNegative();
        assertThat(TableEngine.number("1e3")).isEqualTo(1000.0);
        assertThat(TableEngine.number("NaN")).isNull();
        assertThat(TableEngine.number("Infinity")).isNull();
        assertThat(TableEngine.number("")).isNull();
    }

    @Test
    void testFormatForPath() {
        assertThat(Format.forPath(WorkspacePath.parse("/attachments/1.md"))).isEqualTo(Format.MARKDOWN);
        assertThat(Format.forPath(WorkspacePath.parse("/outputs/a.TSV"))).isEqualTo(Format.TSV);
        assertThat(Format.forPath(WorkspacePath.parse("/outputs/a.csv"))).isEqualTo(Format.CSV);
    }

    private long count(final Condition condition) {
        return TableEngine.run(csv, Format.CSV, query().where(List.of(condition)).build()).rowsMatched();
    }

    private static QueryBuilder query() {
        return new QueryBuilder();
    }

    private static final class QueryBuilder {

        private List<Eval> eval = List.of();
        private List<String> select = List.of();
        private List<Condition> where = List.of();
        private List<String> groupBy = List.of();
        private List<Aggregate> aggregates = List.of();
        private boolean distinct;
        private boolean mask;
        private List<Order> orderBy = List.of();
        private Integer limit;

        QueryBuilder eval(final List<Eval> eval) {
            this.eval = eval;
            return this;
        }

        QueryBuilder select(final List<String> select) {
            this.select = select;
            return this;
        }

        QueryBuilder where(final List<Condition> where) {
            this.where = where;
            return this;
        }

        QueryBuilder groupBy(final List<String> groupBy) {
            this.groupBy = groupBy;
            return this;
        }

        QueryBuilder aggregates(final List<Aggregate> aggregates) {
            this.aggregates = aggregates;
            return this;
        }

        QueryBuilder distinct(final boolean distinct) {
            this.distinct = distinct;
            return this;
        }

        QueryBuilder mask(final boolean mask) {
            this.mask = mask;
            return this;
        }

        QueryBuilder orderBy(final List<Order> orderBy) {
            this.orderBy = orderBy;
            return this;
        }

        QueryBuilder limit(final Integer limit) {
            this.limit = limit;
            return this;
        }

        Query build() {
            return new Query(eval, select, where, groupBy, aggregates, distinct, mask, orderBy, limit);
        }
    }
}
