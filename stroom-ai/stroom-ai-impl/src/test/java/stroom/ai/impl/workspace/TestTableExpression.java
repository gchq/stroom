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

package stroom.ai.impl.workspace;

import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableExpression {

    private static final List<String> HEADER = List.of("id", "n");

    @Test
    void testCellsThatAreNumbersAreNumbers() {
        assertThat(TableExpression.value("12")).isInstanceOf(ValLong.class);
        assertThat(TableExpression.value("-1.5")).isInstanceOf(ValDouble.class);
        assertThat(TableExpression.value("0.5")).isInstanceOf(ValDouble.class);
        assertThat(TableExpression.value("0")).isInstanceOf(ValLong.class);
        assertThat(TableExpression.value("99999999999999999999")).isInstanceOf(ValDouble.class);
        // An ID with a leading zero stays as it is.
        assertThat(TableExpression.value("007")).isInstanceOf(ValString.class);
        assertThat(TableExpression.value("fw1")).isInstanceOf(ValString.class);
        assertThat(TableExpression.value("2026-10-18T09:15:00.000Z")).isInstanceOf(ValString.class);
        assertThat(TableExpression.value("")).isSameAs(ValNull.INSTANCE);
        assertThat(TableExpression.value(null)).isSameAs(ValNull.INSTANCE);
    }

    @Test
    void testEvaluate() {
        assertThat(TableExpression.compile("concat(${id}, '-', ${n} + 1)", HEADER).evaluate(List.of("007", "4")))
                .isEqualTo("007-5");
        // Column names match ignoring case, and a short row has no value for what it is missing.
        assertThat(TableExpression.compile("${N}", HEADER).evaluate(List.of("a"))).isEmpty();
        assertThat(TableExpression.compile("${n} * 2", HEADER).evaluate(List.of("a", "x"))).startsWith("ERR");
        assertThat(TableExpression.compile("${n} / 0", HEADER).evaluate(List.of("a", "1"))).isNotBlank();
    }

    @Test
    void testGroups() {
        final TableExpression expression = TableExpression.compile("sum(${n}) / count()", HEADER);
        final TableExpression.Group first = expression.group();
        final TableExpression.Group second = expression.group();
        first.add(List.of("a", "2"));
        first.add(List.of("a", "4"));
        second.add(List.of("b", "10"));

        assertThat(expression.isAggregate()).isTrue();
        // Each group keeps its own values.
        assertThat(first.result()).isEqualTo("3");
        assertThat(second.result()).isEqualTo("10");
    }

    @Test
    void aDashboardColumnsExpressionIsCheckedWithTheDataSourcesFields() {
        assertThat(TableExpression.dashboardProblem("count()", List.of("UserId"))).isEmpty();
        assertThat(TableExpression.dashboardProblem("upperCase(${userid})", List.of("UserId"))).isEmpty();
        // Functions a dashboard has, that the table tool doesn't, are allowed.
        assertThat(TableExpression.dashboardProblem("first(${UserId})", List.of("UserId"))).isEmpty();
        assertThat(TableExpression.dashboardProblem("${annotation:Status}", List.of("UserId"))).isEmpty();
        // Any field, if the data source's aren't known.
        assertThat(TableExpression.dashboardProblem("${Anything}", null)).isEmpty();

        assertThat(TableExpression.dashboardProblem("${Usr}", List.of("UserId")))
                .hasValueSatisfying(problem -> assertThat(problem).contains("names a field 'Usr'"));
        assertThat(TableExpression.dashboardProblem("count((", List.of("UserId")))
                .hasValueSatisfying(problem -> assertThat(problem).contains("can't be read"));
        assertThat(TableExpression.dashboardProblem("cuont()", List.of("UserId")))
                .hasValueSatisfying(problem -> assertThat(problem).contains("Unknown function 'cuont'"));
        assertThat(TableExpression.dashboardProblem(" ", List.of("UserId"))).contains("Give an expression.");
    }
}
