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

package stroom.query.common.v2;

import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.language.functions.ExpressionContext;
import stroom.test.common.AbstractValidatorTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionContextFactory extends AbstractValidatorTest {

    private static final int SEARCH_LIMIT = 11;
    private static final int ANALYTIC_LIMIT = 22;

    private final ExpressionContextFactory expressionContextFactory = new ExpressionContextFactory(
            () -> new AnalyticResultStoreConfig(
                    null, null, null, null, 222, null, null, ANALYTIC_LIMIT, null),
            () -> new SearchResultStoreConfig(
                    null, null, null, null, 111, null, null, null, null, null, null, null, SEARCH_LIMIT),
            () -> null,
            () -> null);

    @ParameterizedTest
    @EnumSource(SourceType.class)
    void testLimitsOfSourceType(final SourceType sourceType) {
        final boolean analytic = sourceType == SourceType.TABLE_BUILDER_ANALYTIC ||
                                 sourceType == SourceType.SCHEDULED_QUERY_ANALYTIC;
        final SearchRequestSource source = SearchRequestSource.builder().sourceType(sourceType).build();
        final ExpressionContext context = expressionContextFactory.createContext(source, null);
        assertThat(context.getMaxCountDistinctValues()).isEqualTo(analytic
                ? ANALYTIC_LIMIT
                : SEARCH_LIMIT);
        assertThat(context.getMaxStringLength()).isEqualTo(analytic
                ? 222
                : 111);
        assertThat(expressionContextFactory.getMaxStringLength(source)).isEqualTo(context.getMaxStringLength());
    }

    @Test
    void testLimitsWithNoSource() {
        assertThat(expressionContextFactory.createContext(null, null).getMaxCountDistinctValues())
                .isEqualTo(SEARCH_LIMIT);
    }

    @ParameterizedTest
    @CsvSource({"-1, false", "0, false", "1, true", "1000000, true", "1000001, false"})
    void testLimitRange(final int limit, final boolean valid) {
        assertThat(validate(new AnalyticResultStoreConfig(
                null, null, null, null, null, null, null, limit, null)).isEmpty()).isEqualTo(valid);
        assertThat(validate(new SearchResultStoreConfig(
                null, null, null, null, null, null, null, null, null, null, null, null, limit)).isEmpty())
                .isEqualTo(valid);
    }

    @Test
    void testDefaultLimits() {
        assertThat(new SearchResultStoreConfig().getMaxCountDistinctValues()).isEqualTo(10_000);
        assertThat(new AnalyticResultStoreConfig().getMaxCountDistinctValues()).isEqualTo(10_000);
        assertThat(new AnalyticResultStoreConfig(
                null, null, null, null, null, null, null, null, null).getMaxCountDistinctValues()).isEqualTo(10_000);
    }
}
