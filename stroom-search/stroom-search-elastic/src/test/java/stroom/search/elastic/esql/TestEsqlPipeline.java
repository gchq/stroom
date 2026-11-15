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

package stroom.search.elastic.esql;

import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEsqlPipeline {

    @Test
    void testStagesAndQuery() {
        final EsqlPipeline pipeline = EsqlPipeline.parse("""
                | WHERE bytes > 0
                | STATS total = SUM(bytes) BY user.name, url.domain
                | SORT total DESC
                | LIMIT 10""");

        assertThat(pipeline.stages())
                .extracting(EsqlPipeline.Stage::command)
                .containsExactly("WHERE", "STATS", "SORT", "LIMIT");
        assertThat(pipeline.query("proxy-logs")).isEqualTo("""
                FROM proxy-logs
                | WHERE bytes > 0
                | STATS total = SUM(bytes) BY user.name, url.domain
                | SORT total DESC
                | LIMIT 10""");
    }

    @Test
    void testLeadingPipeMayBeLeftOut() {
        assertThat(EsqlPipeline.parse("stats n = count(*) by host").query("i"))
                .isEqualTo("FROM i\n| stats n = count(*) by host");
    }

    @Test
    void testEmpty() {
        assertThat(EsqlPipeline.parse(null).stages()).isEmpty();
        assertThat(EsqlPipeline.parse("  // nothing\n").stages()).isEmpty();
        assertThat(EsqlPipeline.parse("").limited(100).pipeline().query("i")).isEqualTo("FROM i\n| LIMIT 100");
    }

    @Test
    void testCommandsAreCaseInsensitive() {
        assertThat(EsqlPipeline.parse("| where a == 1 | Stats c = count(*) | mv_expand x").stages())
                .extracting(EsqlPipeline.Stage::command)
                .containsExactly("WHERE", "STATS", "MV_EXPAND");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FROM other-index",
            "| FROM other-index | STATS c = COUNT(*)",
            "| WHERE a == 1 | from other",
            "| /* hidden */ FROM other",
            "| // comment\n FROM other",
            "| ENRICH policy ON user",
            "| LOOKUP JOIN other ON user",
            "| FORK (WHERE a == 1) (WHERE a == 2)",
            "| ROW a = 1",
            "| SHOW INFO",
            "| INLINESTATS c = COUNT(*)",
            "| COMPLETION answer = prompt WITH { \"inference_id\": \"x\" }",
            "| RERANK \"q\" ON text",
            "| CHANGE_POINT value ON key",
            "| SOMETHING_NEW",
    })
    void testRefusedCommands(final String pipeline) {
        assertThatThrownBy(() -> EsqlPipeline.parse(pipeline))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.COMMAND_NOT_ALLOWED))
                .hasMessageContaining("isn't allowed here")
                .hasMessageContaining("Only these commands");
    }

    @Test
    void testFromRefusalSaysWhy() {
        assertThatThrownBy(() -> EsqlPipeline.parse("FROM logs-* | STATS c = COUNT(*)"))
                .hasMessageContaining("FROM is written for you: give only the commands after it");
        assertThatThrownBy(() -> EsqlPipeline.parse("| LOOKUP JOIN users ON id"))
                .hasMessageContaining("LOOKUP JOIN isn't allowed here: it reads another index");
    }

    @Test
    void testPipesInsideStringsNamesCommentsAndBracketsDontSplit() {
        final EsqlPipeline pipeline = EsqlPipeline.parse("""
                | WHERE message == "a | FROM other" // | FROM other
                | WHERE `odd|name` == 1 /* | FROM other */
                | EVAL x = CONCAT(a, \"""| FROM x\""")
                | WHERE b IN ("|", "| ROW") AND c == \"\\"|\\" FROM\"""");

        assertThat(pipeline.stages())
                .extracting(EsqlPipeline.Stage::command)
                .containsExactly("WHERE", "WHERE", "EVAL", "WHERE");
    }

    @Test
    void testLineCommentCantHideWhatIsAdded() {
        final String query = EsqlPipeline.parse("| STATS c = COUNT(*) BY host // the hosts")
                .limited(50)
                .pipeline()
                .query("i");

        // Each stage on its own line, so the comment ends before the limit added.
        assertThat(query).isEqualTo("FROM i\n| STATS c = COUNT(*) BY host\n| LIMIT 50");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "| WHERE a == \"unterminated",
            "| WHERE a == \"line\nbreak\"",
            "| WHERE `unterminated",
            "| WHERE a == 1 /* unterminated",
            "| WHERE a == 1 /* /* nested */",
            "| WHERE (a == 1",
            "| WHERE a == 1)",
            "| WHERE a == 1 | | LIMIT 1",
            "| WHERE a == 1 |",
            "|",
            "| LIMIT ten",
            "| LIMIT 10 20",
            "| LIMIT 99999999999999999999",
    })
    void testInvalid(final String pipeline) {
        assertThatThrownBy(() -> EsqlPipeline.parse(pipeline))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.INVALID));
    }

    @Test
    void testNestedBlockCommentIsSkipped() {
        assertThat(EsqlPipeline.parse("| WHERE a == 1 /* outer /* inner | FROM x */ still | FROM y */").stages())
                .extracting(EsqlPipeline.Stage::command)
                .containsExactly("WHERE");
    }

    @Test
    void testSemicolonIsRefused() {
        assertThatThrownBy(() -> EsqlPipeline.parse("| WHERE a == 1; FROM other"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("another statement");
        // Inside a string it is only text.
        assertThat(EsqlPipeline.parse("| WHERE a == \";\"").stages()).hasSize(1);
    }

    @Test
    void testLimitAdded() {
        final EsqlPipeline.Limited limited = EsqlPipeline.parse("| STATS c = COUNT(*) BY host").limited(1000);

        assertThat(limited.limit()).isEqualTo(1000);
        assertThat(limited.note()).contains("had no LIMIT");
        assertThat(limited.pipeline().query("i")).endsWith("\n| LIMIT 1000");
    }

    @Test
    void testOwnLimitKept() {
        final EsqlPipeline.Limited limited = EsqlPipeline.parse("| STATS c = COUNT(*) BY host | SORT c DESC "
                                                                + "| LIMIT 10 | KEEP host").limited(1000);

        assertThat(limited.limit()).isEqualTo(10);
        assertThat(limited.note()).isNull();
        assertThat(limited.pipeline().query("i")).doesNotContain("1000");
    }

    @Test
    void testOwnLimitOverTheCapIsCapped() {
        final EsqlPipeline.Limited limited = EsqlPipeline.parse("| SORT a | LIMIT 50000").limited(10_000);

        assertThat(limited.limit()).isEqualTo(10_000);
        assertThat(limited.note()).contains("over 10000");
        assertThat(limited.pipeline().query("i")).endsWith("| LIMIT 50000\n| LIMIT 10000");
    }

    @Test
    void testLimitBeforeStatsDoesntBoundTheRows() {
        // A LIMIT before STATS limits the documents read, not the groups given.
        final EsqlPipeline.Limited limited = EsqlPipeline.parse("| LIMIT 5 | STATS c = COUNT(*) BY host")
                .limited(1000);

        assertThat(limited.limit()).isEqualTo(1000);
        assertThat(limited.pipeline().query("i")).endsWith("| LIMIT 1000");
    }

    @Test
    void testLowestOutputLimitBounds() {
        assertThat(EsqlPipeline.parse("| SORT a | LIMIT 20 | EVAL b = a + 1 | LIMIT 30").limited(1000).limit())
                .isEqualTo(20);
    }

    @Test
    void testForTotal() {
        final EsqlPipeline pipeline = EsqlPipeline.parse("| WHERE bytes > 0 | LIMIT 100000 "
                                                         + "| STATS total = SUM(bytes) BY user "
                                                         + "| WHERE total > 10 | SORT total DESC | LIMIT 10");

        // The LIMIT before STATS is kept: it changes what is grouped.
        assertThat(pipeline.forTotal().query("i")).isEqualTo("""
                FROM i
                | WHERE bytes > 0
                | LIMIT 100000
                | STATS total = SUM(bytes) BY user
                | WHERE total > 10
                | STATS stroom_total_rows = COUNT(*)""");
        assertThat(pipeline.groups()).isTrue();
    }

    @Test
    void testForTotalWithoutStats() {
        assertThat(EsqlPipeline.parse("| WHERE a > 1 | SORT a | LIMIT 5 | KEEP a").forTotal().query("i"))
                .isEqualTo("FROM i\n| WHERE a > 1\n| KEEP a\n| STATS stroom_total_rows = COUNT(*)");
        assertThat(EsqlPipeline.parse("| WHERE a > 1").groups()).isFalse();
        assertThat(EsqlPipeline.parse("| STATS c = COUNT(*)").groups()).isFalse();
    }

    @Test
    void testGroupingFields() {
        final EsqlPipeline pipeline = EsqlPipeline.parse("""
                | EVAL lower = TO_LOWER(message)
                | STATS c = COUNT(*), s = SUM(bytes) BY `user.name`, domain = url.domain, lower,
                    BUCKET(@timestamp, 1 hour), message""");

        // Expressions and names made earlier aren't fields of the index.
        assertThat(pipeline.groupingFields()).containsExactly("user.name", "url.domain", "message");
    }

    @Test
    void testRenamedNamesArentFields() {
        assertThat(EsqlPipeline.parse("| RENAME message AS msg | STATS c = COUNT(*) BY msg").groupingFields())
                .isEmpty();
    }

    @Test
    void testByInsideBracketsIsNotGrouping() {
        assertThat(EsqlPipeline.parse("| STATS c = COUNT(*) WHERE (a == \"BY x\")").groupingFields()).isEmpty();
        assertThat(EsqlPipeline.parse("| STATS c = COUNT(*) WHERE (a == \"BY x\")").groups()).isFalse();
    }
}
