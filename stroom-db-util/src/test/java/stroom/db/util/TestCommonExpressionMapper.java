/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.db.util;


import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.QueryField;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This ought to behave in a consistent way with ExpressionMatcher
 */
class TestCommonExpressionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCommonExpressionMapper.class);

    private static final String DB_FIELD_NAME_1 = "field1";
    private static final String DB_FIELD_NAME_2 = "field2";
    private static final String DB_FIELD_NAME_3 = "field3";

    private static final String FIELD_1_VALUE = "123";
    private static final String FIELD_2_VALUE = "456";
    private static final String FIELD_3_VALUE = "789";

    private static final List<String> FIELD_NAMES = List.of(
            DB_FIELD_NAME_1,
            DB_FIELD_NAME_2,
            DB_FIELD_NAME_3);

    @TestFactory
    List<DynamicTest> makeEmptyOpTests() {

        return Arrays.stream(ExpressionOperator.Op.values())
                .map(opType -> DynamicTest.dynamicTest(opType.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = ExpressionOperator.builder().op(opType).build();

                    final CommonExpressionMapper mapper = new CommonExpressionMapper();
                    final Condition condition = mapper.apply(expressionOperator);

                    LOGGER.info("expressionItem: {}", expressionOperator);
                    LOGGER.info("condition: {}", condition);

                    assertThat(condition).isEqualTo(DSL.noCondition());
                }))
                .toList();
    }

    @TestFactory
    List<DynamicTest> makeDisabledEmptyOpTests() {
        return Arrays.stream(ExpressionOperator.Op.values())
                .map(opType -> DynamicTest.dynamicTest(opType.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                            .op(opType)
                            .enabled(false)
                            .build();

                    final CommonExpressionMapper mapper = new CommonExpressionMapper();
                    final Condition condition = mapper.apply(expressionOperator);

                    LOGGER.info("expressionItem: {}", expressionOperator);
                    LOGGER.info("condition: {}", condition);

                    assertThat(condition).isEqualTo(DSL.noCondition());
                }))
                .toList();
    }

    @Test
    void testNullOperator() {
        final ExpressionOperator expressionOperator = null;

        final Condition condition = doTest(expressionOperator);

        assertThat(condition)
                .isEqualTo(DSL.noCondition());
    }

    @Test
    void testNotEmptyAnd() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addOperator(ExpressionOperator.builder().build())
                .build();

        final Condition condition = doTest(expressionOperator);

        assertThat(condition).isEqualTo(DSL.noCondition());
    }

    @Test
    void testAndEmptyAnd() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // AND { AND {} } == true, so no condition
        assertThat(condition).isEqualTo(DSL.noCondition());
    }

    @Test
    void testAndEmptyNot() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.NOT).build())
                .addTerm(ExpressionTerm.builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        assertThat(condition.toString()).isEqualTo("(field1=123)");
    }

    @Test
    void testAlwaysTrueAnd() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().build())
                .addOperator(ExpressionOperator.builder()
                        .addOperator(ExpressionOperator.builder().build())
                        .addOperator(ExpressionOperator.builder().op(Op.OR).build()).build()
                )
                .build();

        final Condition condition = doTest(expressionOperator);

        // AND { AND {}, AND { AND{}, OR{} } } == true
        assertThat(condition).isEqualTo(DSL.noCondition());
    }

    @Test
    void testAlwaysTrueOr() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.OR)
                .addOperator(ExpressionOperator.builder().build())
                .addTerm(ExpressionTerm.builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // OR { 1=1, field1=123 } == true, terms condensed down to one true condition, so empty list
        assertThat(condition.toString()).isEqualTo("(field1=123)");
    }

    @Test
    void testOrEmptyNot() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.OR)
                .addOperator(ExpressionOperator.builder().op(Op.NOT).build())
                .addOperator(ExpressionOperator.builder().op(Op.NOT).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        assertThat(condition).isEqualTo(DSL.noCondition());
    }

    @Test
    void testNotNot() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addOperator(ExpressionOperator.builder().op(Op.NOT).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        assertThat(condition).isEqualTo(DSL.noCondition());
    }

    @Test
    void testNotWithChildren() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(ExpressionTerm.builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // NOT { field1=123, field2=456 } == not(field1=123) and not(field2=456)
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_2, FIELD_2_VALUE));
        assertThat(condition.toString())
                .contains("not (field1=123)");
    }

    @TestFactory
    List<DynamicTest> testOneTerm() {
        return Stream.of(Op.AND, Op.OR)
                .map(op -> DynamicTest.dynamicTest(op.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                            .op(op)
                            .addTerm(ExpressionTerm.builder()
                                    .field(DB_FIELD_NAME_1)
                                    .condition(ExpressionTerm.Condition.EQUALS)
                                    .value(FIELD_1_VALUE).build())
                            .build();

                    final Condition condition = doTest(expressionOperator);

                    assertThat(condition.toString())
                            .isEqualTo(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
                }))
                .toList();
    }

    @TestFactory
    List<DynamicTest> testTwoTerms() {
        return Stream.of(Op.AND, Op.OR)
                .map(op -> DynamicTest.dynamicTest(op.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                            .op(op)
                            .addTerm(ExpressionTerm.builder()
                                    .field(DB_FIELD_NAME_1)
                                    .condition(ExpressionTerm.Condition.EQUALS)
                                    .value(FIELD_1_VALUE).build())
                            .addTerm(ExpressionTerm.builder()
                                    .field(DB_FIELD_NAME_2)
                                    .condition(ExpressionTerm.Condition.EQUALS)
                                    .value(FIELD_2_VALUE).build())
                            .build();

                    final Condition condition = doTest(expressionOperator);

                    // No way to inspect the conditon tree so have to do contains on toString
                    // No way to check it is an OR either
                    assertThat(condition.toString())
                            .contains(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
                    assertThat(condition.toString())
                            .contains(conditionString(DB_FIELD_NAME_2, FIELD_2_VALUE));
                }))
                .toList();
    }

    @Test
    void testNesting() {
        final ExpressionOperator innerOr = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .addTerm(ExpressionTerm.builder()
                        .field(DB_FIELD_NAME_2)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_2_VALUE).build())
                .build();

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addOperator(innerOr)
                .addTerm(ExpressionTerm.builder()
                        .field(DB_FIELD_NAME_3)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_3_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // No way to inspect the conditon tree so have to do contains on toString
        // No way to check it is an OR either
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_2, FIELD_2_VALUE));
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_3, FIELD_3_VALUE));
    }

    private Condition doTest(final ExpressionItem expressionItem) {
        // create a noddy term mapper that doesn't need any generated code
        final Function<ExpressionTerm, Condition> handler = expressionTerm ->
                DSL.condition(expressionTerm.getField() + "=" + expressionTerm.getValue());

        final CommonExpressionMapper mapper = new CommonExpressionMapper();

        // Set up some noddy term handlers so we can test expression terms
        FIELD_NAMES.forEach(fieldName -> mapper.addHandler(QueryField
                .builder()
                .fldName(fieldName)
                .queryable(true)
                .conditionSet(ConditionSet.DEFAULT_ID)
                .build(), handler));

        final Condition condition = mapper.apply(expressionItem);

        LOGGER.info("expressionItem: {}", expressionItem);
        LOGGER.info("condition: {}", condition);

        return condition;
    }

    private String conditionString(final String fieldName, final String value) {
        return "(" + DB_FIELD_NAME_1 + "=" + FIELD_1_VALUE + ")";
    }
}
