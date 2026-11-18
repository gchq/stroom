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

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.query.api.TimeRange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void timeRangeAppliesOnTheTimeColumnUnlessTheNativeQueryUsesIt() {
        final Query query = Query.builder().expression(WHERE).timeRange(TIME_RANGE).build();

        final TimeRanges.Applied onColumn = TimeRanges.apply(query, false, "event_time", "Ignored");
        final TimeRanges.Applied inNativeQuery = TimeRanges.apply(query, true, "event_time", "Ignored");

        assertThat(onColumn.filtersOnTimeColumn()).isTrue();
        assertThat(onColumn.expression()).isEqualTo(TimeRanges.addTimeRange(WHERE, TIME_RANGE, "event_time"));
        assertThat(onColumn.warnings()).isEmpty();
        assertThat(inNativeQuery.filtersOnTimeColumn()).isFalse();
        assertThat(inNativeQuery.expression()).isSameAs(WHERE);
        assertThat(inNativeQuery.warnings()).isEmpty();
    }

    @Test
    void timeRangeWithNoTimeColumnIsIgnoredWithAWarning() {
        final Query query = Query.builder().expression(WHERE).timeRange(TIME_RANGE).build();

        final TimeRanges.Applied applied = TimeRanges.apply(query, false, " ", "Ignored");

        assertThat(applied.filtersOnTimeColumn()).isFalse();
        assertThat(applied.expression()).isSameAs(WHERE);
        assertThat(applied.warnings()).containsExactly("Ignored");
        assertThat(TimeRanges.apply(Query.builder().expression(WHERE).build(), false, null, "Ignored").warnings())
                .isEmpty();
    }

    @Test
    void timeColumnMustBeADateColumn() {
        final DataConnectionDoc doc = DataConnectionDoc.builder().uuid("uuid-1").name("Events").build();
        final List<DataColumn> columns = List.of(
                new DataColumn("Event_Time", DataColumnType.DATE),
                new DataColumn("name", DataColumnType.TEXT));

        TimeRanges.requireDateColumn(doc, columns, "event_time", "column", "");
        assertThatThrownBy(() -> TimeRanges.requireDateColumn(doc, columns, "name", "attribute", ""))
                .hasMessageContaining("is not a Date attribute");
        assertThatThrownBy(() -> TimeRanges.requireDateColumn(doc, columns, "other", "attribute", " Add it."))
                .hasMessageEndingWith("is not one of the attributes of Data Connection 'Events'. Add it.");
    }
}
