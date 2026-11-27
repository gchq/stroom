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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDateTermResolver {

    private static final List<QueryField> FIELDS = List.of(
            QueryField.createDate("Time"),
            QueryField.createText("Name"));
    private static final DateTimeSettings LONDON = DateTimeSettings.builder()
            .referenceTime(Instant.parse("2024-07-01T09:00:00Z").toEpochMilli())
            .timeZone(UserTimeZone.builder().use(Use.ID).id("Europe/London").build())
            .build();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "day()|2024-06-30T23:00:00.000Z",
            "now()-1d|2024-06-30T09:00:00.000Z",
            "2024-07-01T00:00:00|2024-06-30T23:00:00.000Z",
            "2024-07-01T00:00:00Z|2024-07-01T00:00:00.000Z",
            "'day()-1d,\tday()\n'|2024-06-29T23:00:00.000Z,2024-06-30T23:00:00.000Z",
            "day()-1d, day()|2024-06-29T23:00:00.000Z,2024-06-30T23:00:00.000Z",
            // Left for whatever reads the term to report.
            "yesterday|yesterday",
            "day(), yesterday|day(), yesterday",
            "now()+9223372036854775807w|now()+9223372036854775807w"})
    void dateValues(final String value, final String expected) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm("Time", Condition.GREATER_THAN, value)
                .build();

        assertThat(DateTermResolver.resolve(expression, FIELDS, LONDON).getChildren())
                .containsExactly(ExpressionTerm.builder()
                        .field("Time")
                        .condition(Condition.GREATER_THAN)
                        .value(expected)
                        .build());
    }

    @Test
    void theRestOfTheExpressionIsKept() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm("Name", Condition.EQUALS, "day()")
                .addTerm("Time", Condition.IS_NOT_NULL, null)
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .enabled(false)
                        .addTerm("Time", Condition.LESS_THAN, "day()")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm("Time", Condition.LESS_THAN, "day()")
                        .build())
                .build();

        assertThat(DateTermResolver.resolve(expression, FIELDS, LONDON)).isEqualTo(ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm("Name", Condition.EQUALS, "day()")
                .addTerm("Time", Condition.IS_NOT_NULL, null)
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .enabled(false)
                        // Nothing reads it.
                        .addTerm("Time", Condition.LESS_THAN, "day()")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm("Time", Condition.LESS_THAN, "2024-06-30T23:00:00.000Z")
                        .build())
                .build());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mars/Base|Unknown time zone 'Mars/Base'",
            "''|There is no time zone"})
    void unknownTimeZone(final String zone, final String message) {
        final DateTimeSettings unknown = LONDON.copy()
                .timeZone(UserTimeZone.builder().use(Use.ID).id(zone).build())
                .build();

        for (final String value : List.of("day()", "now()-1d", "2024-07-01T00:00:00", "2024-07-01T00:00:00Z, day() ",
                "2024-07-01T00:00:00Z,\nday()", "\tday()\n")) {
            assertThatThrownBy(() -> resolve("Time", value, unknown))
                    .as(value)
                    .isInstanceOf(DateTimeException.class)
                    .hasMessageContaining(message);
        }
        // The time zone doesn't matter to these.
        assertThat(resolve("Time", "2024-07-01T00:00:00+01:00", unknown)).isEqualTo("2024-06-30T23:00:00.000Z");
        assertThat(resolve("Time", "My Dictionary", unknown)).isEqualTo("My Dictionary");
        assertThat(resolve("Name", "day()", unknown)).isEqualTo("day()");
        final ExpressionOperator disabled = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .enabled(false)
                        .addTerm("Time", Condition.LESS_THAN, "day()")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field("Time")
                        .condition(Condition.LESS_THAN)
                        .value("day()")
                        .enabled(false)
                        .build())
                .build();
        assertThat(DateTermResolver.resolve(disabled, FIELDS, unknown)).isEqualTo(disabled);
        final ExpressionOperator allDisabled = ExpressionOperator.builder()
                .enabled(false)
                .addTerm("Time", Condition.LESS_THAN, "day()")
                .build();
        assertThat(DateTermResolver.resolve(allDisabled, FIELDS, unknown)).isEqualTo(allDisabled);
    }

    @Test
    void nullTermIsKept() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .children(Collections.singletonList(null))
                .build();
        assertThat(DateTermResolver.resolve(expression, FIELDS, LONDON)).isEqualTo(expression);
    }

    private static String resolve(final String field, final String value, final DateTimeSettings dateTimeSettings) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(field, Condition.GREATER_THAN, value)
                .build();
        return ((ExpressionTerm) DateTermResolver.resolve(expression, FIELDS, dateTimeSettings)
                .getChildren()
                .getFirst()).getValue();
    }

    @Test
    void nothingToResolve() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm("Time", Condition.GREATER_THAN, "day()")
                .build();

        assertThat(DateTermResolver.resolve(null, FIELDS, LONDON)).isNull();
        assertThat(DateTermResolver.resolve(expression, FIELDS, null)).isSameAs(expression);
        assertThat(DateTermResolver.resolve(ExpressionOperator.builder().build(), FIELDS, LONDON))
                .isEqualTo(ExpressionOperator.builder().build());
    }
}
