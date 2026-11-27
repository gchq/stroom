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

package stroom.analytics.impl;

import stroom.analytics.impl.AnalyticsNodeSearchTaskHandler.TableResultConsumer;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Row;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnalyticsNodeSearchTaskHandler {

    // One row whose time can't be read must not lose the others.
    @Test
    void timeThatIsNotADate() {
        final List<Val> times = new ArrayList<>();
        final LongAdder hitCount = new LongAdder();
        final TableResultConsumer consumer = new TableResultConsumer(
                AnalyticRuleDoc.builder().uuid("uuid").name("rule").build(),
                new QueryField[]{AnalyticFields.TIME_FIELD},
                hitCount,
                values -> times.add(values[0]),
                ExpressionOperator.builder().build(),
                new ExpressionMatcher(AnalyticFields.getFieldMap()));
        consumer.columns(List.of(Column.builder().id("time").name("__time__").build()));

        for (final String time : Arrays.asList(
                "2024-02-29T10:00:00.000Z", "2024-02-30T10:00:00.000Z", "N/A", "", null, "2024-03-01T10:00:00.000Z")) {
            consumer.addRow(Row.builder().values(Arrays.asList(time)).build());
        }

        assertThat(times).containsExactly(
                ValDate.create(1_709_200_800_000L),
                ValNull.INSTANCE,
                ValNull.INSTANCE,
                ValNull.INSTANCE,
                ValNull.INSTANCE,
                ValDate.create(1_709_287_200_000L));
        assertThat(hitCount.sum()).isEqualTo(6);
    }
}
