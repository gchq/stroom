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

package stroom.query.language;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Numbers in the conditions of StroomQL that are not in quotes.
class TestSearchRequestFactoryNumber {

    @ParameterizedTest
    @CsvSource({
            "1e5, 100000",
            "1E5, 100000",
            "1e+5, 100000",
            "1e-3, 0.001",
            "1.5e3, 1500",
            "-1e5, -100000",
            "+1e5, +100000",
            "0e0, 0",
            "0e-5, 0",
            "0e999, 0",
            "0e999999999, 0",
            "0.0e5, 0",
            // A number with no exponent is left as it was written.
            "100, 100",
            "1.50, 1.50",
            // Text that is like a number is left alone.
            "host:1e5, host:1e5",
            "abc1e5, abc1e5",
            "'\"1e5\"', 1e5"})
    void value(final String input, final String expected) {
        assertThat(where("Size = " + input)).isEqualTo("AND {Size = " + expected + "}");
    }

    @Test
    void valueWithQuotesIsLeftAsWritten() {
        assertThat(where("Size = '1e5'")).isEqualTo("AND {Size = 1e5}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e308", "1.7e308", "1e-308"})
    void largestAndSmallestValues(final String input) {
        assertThat(where("Size = " + input)).isEqualTo("AND {Size = " + new BigDecimal(input).toPlainString() + "}");
    }

    @Test
    void between() {
        assertThat(where("Size between 1e2 and 1e3")).isEqualTo("AND {Size between 100, 1000}");
    }

    @Test
    void in() {
        assertThat(where("Size in (1e2, 5, 'a1e5')")).isEqualTo("AND {Size in 100, 5, a1e5}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1e",
            "1e5x",
            "1e5e5",
            "1e309",
            "1.8e308",
            "9e308",
            "1e999",
            "1e-999",
            "1e999999999",
            "1e99999999999999999999",
            // Text with an operator in it has to be in quotes.
            "*1e5*",
            "1e5*",
            "*1e5",
            "host-1e5",
            "1e5-abc",
            "/a/1e5",
            "x%1e5",
            "1e5-1",
            "--1e5",
            // An id that starts like a number has to be in quotes.
            "123e4567-e89b-12d3-a456-426614174000",
            "12345678-e89b-12d3-a456-426614174000"})
    void refused(final String input) {
        assertThatThrownBy(() -> where("Size = " + input)).isInstanceOf(TokenException.class);
    }

    @Test
    void having() {
        assertThat(create("from index_view group by Size having Size > 1e5 select Size")
                .getResultRequests()
                .getFirst()
                .getMappings()
                .getFirst()
                .getAggregateFilter()
                .toString())
                .isEqualTo("AND {Size > 100000}");
    }

    @Test
    void refusedIn() {
        assertThatThrownBy(() -> where("Size in (1, 1e999999999)")).isInstanceOf(TokenException.class);
    }

    private String where(final String condition) {
        return create("from index_view where " + condition + " select Size")
                .getQuery()
                .getExpression()
                .toString();
    }

    private SearchRequest create(final String query) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(
                null,
                new QueryKey("test"),
                Query.builder().build(),
                new ArrayList<>(0),
                dateTimeSettings,
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        return new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> null,
                MockSecurityContext.getInstance())
                .create(query, in, expressionContext);
    }
}
