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

package stroom.ai.impl.tools.sequence;

import stroom.ai.impl.tools.sequence.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Step;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests [StreamFilters]: a step's conditions as a Stroom expression.
class TestStreamFilters {

    private static final Map<String, FieldType> TYPES = Map.of("action", FieldType.KEYWORD, "bytes", FieldType.LONG,
            "url", FieldType.KEYWORD);

    @Test
    void conditionsBecomeTermsWithPatternsPresenceAndTheCaseOption() {
        final List<Filter> where = List.of(
                new Filter("action", Condition.EQUALS, "Copy", true),
                new Filter("url", Condition.CONTAINS, "abc.com", false),
                new Filter("bytes", Condition.GREATER_THAN, "100", false),
                new Filter("url", Condition.IS_NULL, "", false),
                new Filter("action", Condition.IN, "a,b", false, true, false));
        final List<Filter> oneOf = List.of(
                new Filter("url", Condition.STARTS_WITH, "https", false),
                new Filter("url", Condition.ENDS_WITH, ".zip", false));
        final ExpressionOperator expression = StreamFilters.expression(new Step(1, where, oneOf, List.of("user"),
                false, 1, List.of(), TYPES, true), "Step 1");

        assertThat(expression.op()).isEqualTo(Op.AND);
        final ExpressionTerm copy = (ExpressionTerm) expression.getChildren().getFirst();
        assertThat(copy.getValue()).isEqualTo("Copy");
        assertThat(copy.getOptions()).containsEntry(StreamFilters.CASE_INSENSITIVE, "true");
        assertThat(((ExpressionTerm) expression.getChildren().get(1)).getValue()).isEqualTo("*abc.com*");
        assertThat(((ExpressionTerm) expression.getChildren().get(2)).getCondition())
                .isEqualTo(Condition.GREATER_THAN);
        final ExpressionOperator isNull = (ExpressionOperator) expression.getChildren().get(3);
        assertThat(isNull.op()).isEqualTo(Op.NOT);
        assertThat(((ExpressionTerm) isNull.getChildren().getFirst()).getValue()).isEqualTo("*");
        final ExpressionOperator notIn = (ExpressionOperator) expression.getChildren().get(4);
        assertThat(notIn.op()).isEqualTo(Op.NOT);
        assertThat(((ExpressionTerm) notIn.getChildren().getFirst()).getValue()).isEqualTo("a,b");
        final ExpressionOperator anyOf = (ExpressionOperator) expression.getChildren().get(5);
        assertThat(anyOf.op()).isEqualTo(Op.OR);
        assertThat(anyOf.getChildren()).extracting(item -> ((ExpressionTerm) item).getValue())
                .containsExactly("https*", "*.zip");
    }

    @Test
    void aStepWithNoConditionsHasNoExpressionAndALengthConditionIsRefused() {
        assertThat(StreamFilters.expression(new Step(1, List.of(), List.of("user"), false, 1, List.of(), TYPES),
                "Step 1")).isNull();
        assertThatThrownBy(() -> StreamFilters.expression(new Step(1, List.of(new Filter("url",
                Condition.GREATER_THAN, "100", false, false, true)), List.of("user"), false, 1, List.of(), TYPES),
                "Step 2"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("Step 2: a condition on the length of 'url' can only run as EQL");
    }
}
