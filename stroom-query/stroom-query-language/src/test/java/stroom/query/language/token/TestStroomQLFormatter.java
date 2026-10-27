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

package stroom.query.language.token;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomQLFormatter {

    @Test
    void testParamsAreNotBrokenUp() {
        // The example from gh-5211, which js-beautify turned into `$ {\nTest\n}`.
        final String formatted = StroomQLFormatter.format(
                "from \"Dual\" where ${Test} = \"77\" select ${Test}");

        assertThat(formatted).isEqualTo("""
                from "Dual"
                where ${Test} = "77"
                select ${Test}""");
    }

    @Test
    void testParamsAreUnchanged() {
        final String formatted = StroomQLFormatter.format(
                "from Dual where ${my param} = ${ other } select concat(${a},'${b}')");

        assertThat(formatted).isEqualTo("""
                from Dual
                where ${my param} = ${ other }
                select concat(${a},'${b}')""");
    }

    @Test
    void testAllClauseKeywordsStartNewLine() {
        final String formatted = StroomQLFormatter.format(
                "from Events where a = 1 filter b = 2 eval c = count() window EventTime by 1h "
                + "group by UserId having c > 1 sort by c desc limit 10 select UserId, c show table");

        assertThat(formatted).isEqualTo("""
                from Events
                where a = 1
                filter b = 2
                eval c = count()
                window EventTime by 1h
                group by UserId
                having c > 1
                sort by c desc
                limit 10
                select UserId, c
                show table""");
    }

    @Test
    void testWhitespaceIsNormalised() {
        final String formatted = StroomQLFormatter.format(
                "  \n\tfrom   Events\t\twhere  a  =  1   \n\n\n   select a , b  \n\n");

        assertThat(formatted).isEqualTo("""
                from Events
                where a = 1
                select a , b""");
    }

    @Test
    void testContinuationLinesAreIndented() {
        final String formatted = StroomQLFormatter.format("""
                from Events
                where a = 1
                and b = 2

                        or c = 3
                select a,
                b""");

        assertThat(formatted).isEqualTo("""
                from Events
                where a = 1
                    and b = 2
                    or c = 3
                select a,
                    b""");
    }

    @Test
    void testBetweenIsNotSplit() {
        final String formatted = StroomQLFormatter.format(
                "from Events where EventTime between 2000-01-01T00:00:00.000Z and now() select EventTime");

        assertThat(formatted).isEqualTo("""
                from Events
                where EventTime between 2000-01-01T00:00:00.000Z and now()
                select EventTime""");
    }

    @Test
    void testQuotedStringsAreUnchanged() {
        final String formatted = StroomQLFormatter.format(
                "from Events where a = \"  where   x \\\" select \" and b = '  ${c}\n  from  ' select a");

        assertThat(formatted).isEqualTo("""
                from Events
                where a = "  where   x \\" select " and b = '  ${c}
                  from  '
                select a""");
    }

    @Test
    void testLineCommentIsAlwaysFollowedByNewLine() {
        // If the comment were joined to the next line it would comment out the where clause.
        final String formatted = StroomQLFormatter.format("""
                from Events // the source
                where a = 1 // the filter
                select a""");

        assertThat(formatted).isEqualTo("""
                from Events // the source
                where a = 1 // the filter
                select a""");
    }

    @Test
    void testCommentsOnOwnLine() {
        final String formatted = StroomQLFormatter.format("""
                // Leading comment
                from Events
                  // Describes the where clause
                where a = 1
                  // Describes the and
                  and b = 2
                /* Block comment
                   across lines */
                select a
                // Trailing comment
                """);

        assertThat(formatted).isEqualTo("""
                // Leading comment
                from Events
                // Describes the where clause
                where a = 1
                    // Describes the and
                    and b = 2
                /* Block comment
                   across lines */
                select a
                // Trailing comment""");
    }

    @Test
    void testInlineBlockCommentIsKept() {
        final String formatted = StroomQLFormatter.format(
                "from Events where a = /* one */ 1 select a");

        assertThat(formatted).isEqualTo("""
                from Events
                where a = /* one */ 1
                select a""");
    }

    @Test
    void testNoSpaceIsAdded() {
        // Only existing whitespace is changed, so tokens that were adjacent stay adjacent.
        final String formatted = StroomQLFormatter.format("from Events where a=1 select a,b");

        assertThat(formatted).isEqualTo("""
                from Events
                where a=1
                select a,b""");
    }

    @Test
    void testUnterminatedQuote() {
        final String formatted = StroomQLFormatter.format("from Events where a = \"abc");

        assertThat(formatted).isEqualTo("""
                from Events
                where a = "abc""");
    }

    @Test
    void testFormatIsIdempotent() {
        final String once = StroomQLFormatter.format("""
                // Leading comment
                from "Dual"   where ${Test} = "77"
                and b  = 2 // trailing
                eval c =   concat(${Test}, 'x')
                  /* note */
                select ${Test},
                c""");
        final String twice = StroomQLFormatter.format(once);

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void testOnlyWhitespaceIsChanged() {
        final String query = """
                from "Dual" where ${Test} = "77" and x = 'a  b' // comment
                eval y = if(x = 1, ${Test}, "y") /* c */ select ${Test}, y""";
        final String formatted = StroomQLFormatter.format(query);

        assertThat(nonWhitespaceTokens(formatted)).isEqualTo(nonWhitespaceTokens(query));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\n\t "})
    void testNullOrBlankIsUnchanged(final String query) {
        assertThat(StroomQLFormatter.format(query)).isEqualTo(query);
    }

    private static List<String> nonWhitespaceTokens(final String query) {
        final List<Token> tokens = Tokeniser.parse(query);
        return tokens.stream()
                .filter(token -> !TokenType.WHITESPACE.equals(token.getTokenType()))
                .map(token -> token.getTokenType() + ":" + token.getText())
                .toList();
    }
}
