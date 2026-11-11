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

package stroom.externaldatasource.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSqlStatementParser {

    @Test
    void placeholdersBecomeQuestionMarksInOrder() {
        final ParsedSql parsed = SqlStatementParser.parse(
                "select * from events where time >= :from and time < :to and feed = :feed");

        assertThat(parsed.jdbcSql()).isEqualTo("select * from events where time >= ? and time < ? and feed = ?");
        assertThat(parsed.placeholders()).containsExactly(
                new SqlPlaceholder("from", null),
                new SqlPlaceholder("to", null),
                new SqlPlaceholder("feed", null));
        assertThat(parsed.usesTimeRange()).isTrue();
    }

    @Test
    void placeholderCanCarryAFormat() {
        final ParsedSql parsed = SqlStatementParser.parse("select * from logs where dt >= :from{yyyy-MM-dd}");

        assertThat(parsed.jdbcSql()).isEqualTo("select * from logs where dt >= ?");
        assertThat(parsed.placeholders()).containsExactly(new SqlPlaceholder("from", "yyyy-MM-dd"));
    }

    @Test
    void repeatedPlaceholderIsBoundEachTime() {
        final ParsedSql parsed = SqlStatementParser.parse("select :a, :a");

        assertThat(parsed.jdbcSql()).isEqualTo("select ?, ?");
        assertThat(parsed.placeholders()).hasSize(2);
    }

    @Test
    void sqlWithoutPlaceholdersDoesNotUseTheTimeRange() {
        final ParsedSql parsed = SqlStatementParser.parse("select name from staff");

        assertThat(parsed.placeholders()).isEmpty();
        assertThat(parsed.usesTimeRange()).isFalse();
        assertThat(parsed.sql()).isEqualTo("select name from staff");
    }

    @Test
    void colonsInQuotesAndCommentsAreNotPlaceholders() {
        final String sql = """
                select ':notThis', "col:umn", `other:col` -- :nor this
                /* :or this */ from t where a = :yes""";

        final ParsedSql parsed = SqlStatementParser.parse(sql);

        assertThat(parsed.placeholders()).containsExactly(new SqlPlaceholder("yes", null));
        assertThat(parsed.jdbcSql()).endsWith("where a = ?").contains("':notThis'", "/* :or this */");
    }

    @Test
    void escapedQuotesStayInsideTheString() {
        final ParsedSql parsed = SqlStatementParser.parse("select 'it''s :not', 'back\\' :slash' where x = :x");

        assertThat(parsed.placeholders()).containsExactly(new SqlPlaceholder("x", null));
    }

    @Test
    void postgresDollarQuotedStringsAreLeftAlone() {
        final ParsedSql parsed = SqlStatementParser.parse(
                "select $$it's :not? here$$, $tag$ nor :this $tag$ where a = :yes");

        assertThat(parsed.placeholders()).containsExactly(new SqlPlaceholder("yes", null));
        assertThat(parsed.jdbcSql()).isEqualTo("select $$it's :not? here$$, $tag$ nor :this $tag$ where a = ?");
    }

    @Test
    void dollarInANameIsNotAQuote() {
        // MySQL allows '$' in identifiers.
        final ParsedSql parsed = SqlStatementParser.parse("select a$b$c from t where x = :x");

        assertThat(parsed.placeholders()).containsExactly(new SqlPlaceholder("x", null));
    }

    @Test
    void unclosedDollarQuoteIsRefused() {
        assertThatThrownBy(() -> SqlStatementParser.parse("select $$oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$$")
                .hasMessageContaining("not closed");
    }

    @Test
    void hashIsNotTreatedAsAComment() {
        // '#' starts a comment in MySQL but is an operator in PostgreSQL, so it is not special and a
        // placeholder after it is still bound. The class description advises '--' comments.
        final ParsedSql parsed = SqlStatementParser.parse("select a # b from t where c = :c");

        assertThat(parsed.jdbcSql()).isEqualTo("select a # b from t where c = ?");
    }

    @Test
    void firstKeywordSkipsSpaceCommentsAndBrackets() {
        assertThat(SqlStatementParser.firstKeyword("  SELECT 1")).isEqualTo("select");
        assertThat(SqlStatementParser.firstKeyword("-- note\n/* more */ ((with t as (select 1) select * from t))"))
                .isEqualTo("with");
        assertThat(SqlStatementParser.firstKeyword("insert into t values (1)")).isEqualTo("insert");
        assertThat(SqlStatementParser.firstKeyword("   ")).isEmpty();
        assertThat(SqlStatementParser.firstKeyword(null)).isEmpty();
    }

    @Test
    void postgresCastIsNotAPlaceholder() {
        final ParsedSql parsed = SqlStatementParser.parse("select id::text, :value::int");

        assertThat(parsed.jdbcSql()).isEqualTo("select id::text, ?::int");
        assertThat(parsed.placeholders()).containsExactly(new SqlPlaceholder("value", null));
    }

    @Test
    void colonBeforeANumberIsNotAPlaceholder() {
        // An array slice, or a time literal outside quotes.
        final ParsedSql parsed = SqlStatementParser.parse("select arr[1:2] from t");

        assertThat(parsed.placeholders()).isEmpty();
        assertThat(parsed.jdbcSql()).isEqualTo("select arr[1:2] from t");
    }

    @Test
    void finalSemicolonIsDropped() {
        final ParsedSql parsed = SqlStatementParser.parse("  select 1;  \n");

        assertThat(parsed.jdbcSql()).isEqualTo("select 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select 1; delete from staff",
            "select 1;;",
            "select ';' from t",
            "select 1 -- ; comment"})
    void semicolonAnywhereButTheEndIsRefused(final String sql) {
        assertThatThrownBy(() -> SqlStatementParser.parse(sql))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single statement");
    }

    @Test
    void questionMarkIsRefused() {
        assertThatThrownBy(() -> SqlStatementParser.parse("select * from t where id = ?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("named placeholder");
    }

    @Test
    void questionMarkInQuotesIsAllowed() {
        final ParsedSql parsed = SqlStatementParser.parse("select 'why?' from t");

        assertThat(parsed.jdbcSql()).isEqualTo("select 'why?' from t");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n"})
    void missingSqlIsRefused(final String sql) {
        assertThatThrownBy(() -> SqlStatementParser.parse(sql))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No SQL");
    }

    @Test
    void unclosedQuoteIsRefused() {
        assertThatThrownBy(() -> SqlStatementParser.parse("select 'oops from t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not closed");
    }

    @Test
    void unclosedCommentIsRefused() {
        assertThatThrownBy(() -> SqlStatementParser.parse("select 1 /* oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not closed");
    }

    @Test
    void unclosedFormatIsRefused() {
        assertThatThrownBy(() -> SqlStatementParser.parse("select :from{yyyy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":from");
    }
}
