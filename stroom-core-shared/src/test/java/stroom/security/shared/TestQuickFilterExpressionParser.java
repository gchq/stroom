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

package stroom.security.shared;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuickFilterExpressionParser {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            // Nothing to quote
            "display:alice                 | display:alice",
            "alice                         | alice",
            "''                            | ''",
            "NULL                          | NULL",
            // Spaces
            "display:Alice Anderson        | \"display:Alice Anderson\"",
            "Alice Anderson                | \"Alice Anderson\"",
            // Double quotes are escaped
            "display:Bob \"The Builder\"   | \"display:Bob \\\"The Builder\\\"\"",
            "display:O\"Neil               | \"display:O\\\"Neil\""
    })
    void testQuote(final String token, final String expected) {
        assertThat(QuickFilterExpressionParser.quote(token))
                .isEqualTo(expected);
    }

    @Test
    void testParse_unquotedSpaceSplitsTheToken() {
        // Without quotes the display name is split into two terms, the second for the default fields
        final ExpressionOperator expression = parse("display:Alice Anderson");

        assertThat(expression.getChildren())
                .hasSize(2);
    }

    @Test
    void testParse_quotedTokenWithSpace() {
        final ExpressionOperator expression = parse(QuickFilterExpressionParser.quote("display:Alice Anderson"));

        assertSingleTerm(expression, UserFields.FIELD_DISPLAY_NAME, "*Alice Anderson*");
    }

    @Test
    void testParse_quotedTokenWithQuotes() {
        final ExpressionOperator expression = parse(
                QuickFilterExpressionParser.quote("display:Bob \"The Builder\""));

        assertSingleTerm(expression, UserFields.FIELD_DISPLAY_NAME, "*Bob \"The Builder\"*");
    }

    @Test
    void testParse_quotedTokenAmongOthers() {
        final ExpressionOperator expression = parse(
                QuickFilterExpressionParser.quote("display:Alice Anderson") + " full:smith");

        final List<ExpressionItem> children = expression.getChildren();
        assertThat(children)
                .hasSize(2);
        assertTerm(children.get(0), UserFields.FIELD_DISPLAY_NAME, "*Alice Anderson*");
        assertTerm(children.get(1), UserFields.FIELD_FULL_NAME, "*smith*");
    }

    private static ExpressionOperator parse(final String userInput) {
        return QuickFilterExpressionParser.parse(
                userInput,
                UserFields.DEFAULT_FIELDS,
                UserFields.ALL_FIELDS_MAP);
    }

    private static void assertSingleTerm(final ExpressionOperator expression,
                                         final String expectedField,
                                         final String expectedValue) {
        assertThat(expression.getChildren())
                .hasSize(1);
        assertTerm(expression.getChildren().getFirst(), expectedField, expectedValue);
    }

    private static void assertTerm(final ExpressionItem item,
                                   final String expectedField,
                                   final String expectedValue) {
        assertThat(item)
                .isInstanceOf(ExpressionTerm.class);
        final ExpressionTerm term = (ExpressionTerm) item;
        assertThat(term.getField())
                .isEqualTo(expectedField);
        assertThat(term.getValue())
                .isEqualTo(expectedValue);
    }
}
