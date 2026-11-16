/*
 * Copyright 2023 Crown Copyright
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTokeniser extends AbstractQueryTest {

    @Override
    protected Path getTestDir() {
        final Path dir = Paths.get("../stroom-query-language/src/test/resources/TestTokeniser");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + dir.toAbsolutePath());
        }
        return dir;
    }

    @Override
    protected String convert(final String input) {
        final List<Token> tokens = Tokeniser.parse(input);
        final StringBuilder sb = new StringBuilder();
        for (final Token token : tokens) {
            token.append(sb, false, 0);
        }
        return sb.toString();
    }

    private static List<Token> withoutWhitespace(final String input) {
        return Tokeniser.parse(input)
                .stream()
                .filter(token -> !TokenType.WHITESPACE.equals(token.getTokenType()))
                .toList();
    }

    @Test
    void tripleQuotedStringIsTakenAsWritten() {
        // Double quotes, single quotes, backslashes and new lines need no escaping.
        final List<Token> tokens = withoutWhitespace("""
                from x sql \"""select "a\\b", 'it''s'
                from t\"""
                """);

        assertThat(tokens).extracting(Token::getTokenType)
                .containsExactly(TokenType.FROM, TokenType.STRING, TokenType.SQL, TokenType.DOUBLE_QUOTED_STRING);
        assertThat(tokens.get(3).getUnescapedText()).isEqualTo("select \"a\\b\", 'it''s'\nfrom t");
        assertThat(tokens.get(3).getText()).startsWith("\"\"\"select").endsWith("from t\"\"\"");
    }

    @Test
    void keywordsAndCommentsInsideTripleQuotesAreText() {
        final List<Token> tokens = withoutWhitespace("from x sql \"\"\"select a // b where c\"\"\" select a");

        assertThat(tokens).extracting(Token::getTokenType).containsExactly(
                TokenType.FROM, TokenType.STRING,
                TokenType.SQL, TokenType.DOUBLE_QUOTED_STRING,
                TokenType.SELECT, TokenType.STRING);
    }

    @Test
    void emptyTripleQuotedString() {
        final List<Token> tokens = withoutWhitespace("from x sql \"\"\"\"\"\"");

        assertThat(tokens.get(3).getTokenType()).isEqualTo(TokenType.DOUBLE_QUOTED_STRING);
        assertThat(tokens.get(3).getUnescapedText()).isEmpty();
    }

    @Test
    void unclosedTripleQuoteIsNotAString() {
        final List<Token> tokens = withoutWhitespace("sql \"\"\"select a");

        assertThat(tokens).extracting(Token::getTokenType).doesNotContain(TokenType.DOUBLE_QUOTED_STRING);
    }

    @Test
    void ordinaryDoubleQuotedStringsAreUnchanged() {
        final List<Token> tokens = withoutWhitespace("from \"my \\\"view\\\"\" select \"\"");

        assertThat(tokens.get(1).getUnescapedText()).isEqualTo("my \"view\"");
        assertThat(tokens.get(3).getTokenType()).isEqualTo(TokenType.DOUBLE_QUOTED_STRING);
        assertThat(tokens.get(3).getUnescapedText()).isEmpty();
    }

    @Test
    void textCanEndWithAQuote() {
        // SQL often ends with a quoted identifier; the last three quotes of a run close the string.
        final List<Token> tokens = withoutWhitespace("from x sql \"\"\"select * from \"events\"\"\"\" select a");

        assertThat(tokens.get(3).getTokenType()).isEqualTo(TokenType.DOUBLE_QUOTED_STRING);
        assertThat(tokens.get(3).getUnescapedText()).isEqualTo("select * from \"events\"");
        assertThat(tokens.get(4).getTokenType()).isEqualTo(TokenType.SELECT);
    }

    @Test
    void tripleQuoteInsideOtherQuotesOrCommentsIsText() {
        final List<Token> tokens = withoutWhitespace("from x where a = ' \"\"\" ' // \"\"\"\nselect a");

        assertThat(tokens).extracting(Token::getTokenType).doesNotContain(TokenType.DOUBLE_QUOTED_STRING);
        assertThat(tokens).extracting(Token::getTokenType).contains(TokenType.SINGLE_QUOTED_STRING, TokenType.SELECT);
    }

    @Test
    void sqlIsOnlyAKeywordStraightAfterFromAndTheDataSource() {
        // A field or data source named sql elsewhere keeps working.
        assertThat(withoutWhitespace("from x where sql = 1 select sql"))
                .extracting(Token::getTokenType)
                .doesNotContain(TokenType.SQL);
        assertThat(withoutWhitespace("from x eval sql = 1 select sql"))
                .extracting(Token::getTokenType)
                .doesNotContain(TokenType.SQL);
        assertThat(withoutWhitespace("from sql select a"))
                .extracting(Token::getTokenType)
                .containsExactly(TokenType.FROM, TokenType.STRING, TokenType.SELECT, TokenType.STRING);
    }

    @Test
    void sqlAfterTheDataSourceAndACommentIsAKeyword() {
        assertThat(withoutWhitespace("from \"my source\" // the data\nsql 'select 1' select a"))
                .extracting(Token::getTokenType)
                .contains(TokenType.SQL);
    }

    @Test
    void openTripleQuotedStringIsFound() {
        // As it is while SQL is typed, so the query editor offers no StroomQL help inside it.
        assertThat(Tokeniser.endsInsideTripleQuotedString("from x sql \"\"\"select a from ")).isTrue();
        assertThat(Tokeniser.endsInsideTripleQuotedString("from x sql \"\"\"select a\"\"\" ")).isFalse();
        assertThat(Tokeniser.endsInsideTripleQuotedString("from x where a = 'b ")).isFalse();
        assertThat(Tokeniser.endsInsideTripleQuotedString("")).isFalse();
        assertThat(Tokeniser.endsInsideTripleQuotedString(null)).isFalse();
    }
}
