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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.Tokeniser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestParamFactory {

    private final ParamFactory paramFactory = new ParamFactory(Map.of());
    private final ExpressionContext expressionContext = ExpressionContext.builder()
            .dateTimeSettings(DateTimeSettings.builder()
                    .timeZone(UserTimeZone.builder().use(Use.ID).id("Europe/London").build())
                    .build())
            .build();

    @ParameterizedTest
    @CsvSource({
            "5ms,5",
            "1s,1000",
            "1m,60000",
            "1h,3600000",
            "1d,86400000",
            "1w,604800000",
            "1000000ns,1"})
    void testDuration(final String literal, final long expected) {
        assertThat(create(literal)).isEqualTo(ValDuration.create(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "1M,months and years vary in length",
            "1y,months and years vary in length",
            "1ns,held to the millisecond",
            "1H,Unknown unit 'H'",
            "1D,Unknown unit 'D'"})
    void testBadDuration(final String literal, final String message) {
        assertThatThrownBy(() -> create(literal))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @CsvSource({
            "2024-07-01T09:00:00.000Z,2024-07-01T09:00:00Z",
            "2024-07-01T09:00:00Z,2024-07-01T09:00:00Z",
            "2024-07-01T09:00:00.5Z,2024-07-01T09:00:00.500Z",
            "2024-07-01T09:00:00+01:00,2024-07-01T08:00:00Z",
            "2024-07-01T09:00:00.000-0530,2024-07-01T14:30:00Z",
            // A date with no offset is in the time zone of the query, which is an hour ahead of UTC in July.
            "2024-07-01T09:00:00.000,2024-07-01T08:00:00Z",
            "2024-07-01T09:00:00,2024-07-01T08:00:00Z",
            "2024-01-01T09:00:00,2024-01-01T09:00:00Z"})
    void testDate(final String literal, final Instant expected) {
        assertThat(create(literal, TokenType.DATE_TIME)).isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

    @Test
    void testDateLessDate() {
        final List<Token> tokens = Tokeniser.parse("2024-07-02T00:00:00-2024-07-01T00:00:00");

        assertThat(tokens).extracting(Token::getTokenType)
                .containsExactly(TokenType.DATE_TIME, TokenType.MINUS, TokenType.DATE_TIME);
        assertThat(tokens.getFirst().getText()).isEqualTo("2024-07-02T00:00:00");
    }

    @Test
    void testDateWithNoContextIsUtc() {
        assertThat(paramFactory.create(null, new FieldIndex(), Tokeniser.parse("2024-07-01T09:00:00").getFirst()))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T09:00:00Z").toEpochMilli()));
    }

    @Test
    void testBadDate() {
        assertThatThrownBy(() -> create("2024-13-01T09:00:00Z", TokenType.DATE_TIME))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("2024-13-01T09:00:00Z");
    }

    @Test
    void testCompoundDuration() {
        assertThatThrownBy(() -> create("1d2h"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("is not one number and one unit");
    }

    // A column of a query is compiled from the text of the expression that was parsed, not from what the
    // query's author wrote.
    @ParameterizedTest
    @ValueSource(strings = {"5ms", "1500ms", "61s", "90s", "90m", "3661s", "36h", "1d", "1w"})
    void testDurationIsKeptWhenItsExpressionIsParsedAgain(final String literal) throws ParseException {
        final String text = parse("${a}+" + literal);

        assertThat(parse(text)).isEqualTo(text);
        assertThat(create(text.substring("${a}+".length()))).isEqualTo(create(literal));
    }

    private String parse(final String expression) throws ParseException {
        return new ExpressionParser(paramFactory)
                .parse(expressionContext, new FieldIndex(), expression)
                .toString();
    }

    private Param create(final String literal) {
        return create(literal, TokenType.DURATION);
    }

    private Param create(final String literal, final TokenType tokenType) {
        final List<Token> tokens = Tokeniser.parse(literal);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenType()).isEqualTo(tokenType);
        return paramFactory.create(expressionContext, new FieldIndex(), tokens.getFirst());
    }
}
