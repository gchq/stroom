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

package stroom.dataconnection.impl;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimeRanges {

    private static final TimeRange TIME_RANGE = new TimeRange(null, "2026-11-01T00:00:00.000Z",
            "2026-11-10T00:00:00.000Z");
    private static final ExpressionOperator WHERE = ExpressionOperator.builder()
            .addTerm("name", Condition.EQUALS, "Jones")
            .build();

    @Test
    void timeRangeFiltersOnTheTimeColumn() {
        final ExpressionOperator expression = TimeRanges.addTimeRange(WHERE, TIME_RANGE, "event_time");

        assertThat(expression.op()).isEqualTo(Op.AND);
        assertThat(expression.getChildren()).hasSize(3);
        final ExpressionTerm from = (ExpressionTerm) expression.getChildren().get(0);
        final ExpressionTerm to = (ExpressionTerm) expression.getChildren().get(1);
        assertThat(from.getField()).isEqualTo("event_time");
        assertThat(from.getCondition()).isEqualTo(Condition.GREATER_THAN_OR_EQUAL_TO);
        assertThat(from.getValue()).isEqualTo(TIME_RANGE.getFrom());
        assertThat(to.getCondition()).isEqualTo(Condition.LESS_THAN);
        assertThat(to.getValue()).isEqualTo(TIME_RANGE.getTo());
        assertThat(expression.getChildren().get(2)).isEqualTo(WHERE);
    }

    @Test
    void timeRangeIsNotUsedWithoutATimeColumn() {
        assertThat(TimeRanges.addTimeRange(null, TIME_RANGE, null)).isNull();
        assertThat(TimeRanges.addTimeRange(WHERE, TIME_RANGE, " ")).isSameAs(WHERE);
    }

    @Test
    void openEndedTimeRangeAddsOneTerm() {
        final ExpressionOperator expression = TimeRanges.addTimeRange(
                null,
                new TimeRange(null, "2026-11-01T00:00:00.000Z", null),
                "event_time");

        assertThat(expression.getChildren()).hasSize(1);
    }

    @Test
    void noTimeRangeLeavesTheExpressionAlone() {
        assertThat(TimeRanges.addTimeRange(WHERE, new TimeRange(null, null, null), "event_time")).isSameAs(WHERE);
        assertThat(TimeRanges.addTimeRange(WHERE, null, "event_time")).isSameAs(WHERE);
    }

    @Test
    void timeRangeIsSetByEitherEnd() {
        assertThat(TimeRanges.isSet(null)).isFalse();
        assertThat(TimeRanges.isSet(new TimeRange(null, "", " "))).isFalse();
        assertThat(TimeRanges.isSet(new TimeRange(null, null, "2026-11-10T00:00:00.000Z"))).isTrue();
    }
}
