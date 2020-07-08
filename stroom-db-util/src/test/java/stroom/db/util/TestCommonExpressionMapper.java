package stroom.db.util;


import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
                .map(opType -> {
                    return DynamicTest.dynamicTest(opType.getDisplayValue(), () -> {
                        final ExpressionOperator expressionOperator = new ExpressionOperator(
                                true,
                                opType,
                                Collections.emptyList());

                        final CommonExpressionMapper mapper = new CommonExpressionMapper();

                        final Condition condition = mapper.apply(expressionOperator);

                        LOGGER.info("expressionItem: {}", expressionOperator);
                        LOGGER.info("condition: {}", condition);

                        if (expressionOperator.op().equals(ExpressionOperator.Op.NOT)) {
                            assertThat(condition)
                                    .isEqualTo(DSL.falseCondition());
                        } else {
                            assertThat(condition)
                                    .isEqualTo(DSL.trueCondition());
                        }
                    });
                })
                .collect(Collectors.toList());
    }

    @Test
    void testNotEmptyAnd() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.NOT)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, Collections.emptyList()))
                .build();

        final Condition condition = doTest(expressionOperator);

        // NOT { AND {} } == false
        assertThat(condition)
                .isEqualTo(DSL.falseCondition());
    }

    @Test
    void testAndEmptyAnd() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.AND)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, Collections.emptyList()))
                .build();

        final Condition condition = doTest(expressionOperator);

        // AND { AND {} } == true, so no condition
        assertThat(condition)
                .isEqualTo(DSL.trueCondition());
    }

    @Test
    void testAlwaysFalseAnd() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.AND)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.NOT, Collections.emptyList()))
                .addTerm(new ExpressionTerm.Builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // AND { 1=0, field1=123 } == false, terms condensed down to one false condition
        assertThat(condition)
                .isEqualTo(DSL.falseCondition());
    }

    @Test
    void testAlwaysTrueAnd() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, Collections.emptyList()))
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, List.of(
                        new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .build(),
                        new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .build()
                )))
                .build();

        final Condition condition = doTest(expressionOperator);

        // AND { AND {}, AND { AND{}, OR{} } } == true
        assertThat(condition)
                .isEqualTo(DSL.trueCondition());
    }

    @Test
    void testAlwaysTrueOr() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.OR)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, Collections.emptyList()))
                .addTerm(new ExpressionTerm.Builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // OR { 1=1, field1=123 } == true, terms condensed down to one true condition, so empty list
        assertThat(condition)
                .isEqualTo(DSL.trueCondition());
    }

    @Test
    void testAlwaysFalseOr() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.OR)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.NOT, Collections.emptyList()))
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.NOT, Collections.emptyList()))
                .build();

        final Condition condition = doTest(expressionOperator);

        // OR { NOT{}, NOT{} } == true, terms condensed down to one true condition, so empty list
        assertThat(condition)
                .isEqualTo(DSL.falseCondition());
    }

    @Test
    void testNotNot() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.NOT)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.NOT, Collections.emptyList()))
                .build();

        final Condition condition = doTest(expressionOperator);

        // NOT { NOT {} } == true so simplifies to no condition.
        assertThat(condition)
                .isEqualTo(DSL.trueCondition());
    }

    @Test
    void testNotWithChildren() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.NOT)
                .addTerm(new ExpressionTerm.Builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .addTerm(new ExpressionTerm.Builder()
                        .field(DB_FIELD_NAME_2)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_2_VALUE).build())
                .build();

        final Condition condition = doTest(expressionOperator);

        // NOT { field1=123, field2=456 } == not(field1=123) and not(field2=456)
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
        assertThat(condition.toString())
                .contains(conditionString(DB_FIELD_NAME_2, FIELD_2_VALUE));
        assertThat(condition.toString())
                .contains("and");
    }

    @TestFactory
    List<DynamicTest> testOneTerm() {
        return Stream.of(ExpressionOperator.Op.AND, ExpressionOperator.Op.OR)
                .map(op -> DynamicTest.dynamicTest(op.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                            true, op)
                            .addTerm(new ExpressionTerm.Builder()
                                    .field(DB_FIELD_NAME_1)
                                    .condition(ExpressionTerm.Condition.EQUALS)
                                    .value(FIELD_1_VALUE).build())
                            .build();

                    final Condition condition = doTest(expressionOperator);

                    assertThat(condition.toString())
                            .isEqualTo(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    List<DynamicTest> testTwoTerms() {
        return Stream.of(ExpressionOperator.Op.AND, ExpressionOperator.Op.OR)
                .map(op -> DynamicTest.dynamicTest(op.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                            true, op)
                            .addTerm(new ExpressionTerm.Builder()
                                    .field(DB_FIELD_NAME_1)
                                    .condition(ExpressionTerm.Condition.EQUALS)
                                    .value(FIELD_1_VALUE).build())
                            .addTerm(new ExpressionTerm.Builder()
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
                .collect(Collectors.toList());
    }

    @Test
    void testNesting() {
        final ExpressionOperator innerOr = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.OR)
                .addTerm(new ExpressionTerm.Builder()
                        .field(DB_FIELD_NAME_1)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_1_VALUE).build())
                .addTerm(new ExpressionTerm.Builder()
                        .field(DB_FIELD_NAME_2)
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value(FIELD_2_VALUE).build())
                .build();

        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.AND)
                .addOperator(innerOr)
                .addTerm(new ExpressionTerm.Builder()
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
        Function<ExpressionTerm, Condition> handler = expressionTerm -> {
            return DSL.condition(expressionTerm.getField() + "=" + expressionTerm.getValue());
        };

        final CommonExpressionMapper mapper = new CommonExpressionMapper();

        // Set up some noddy term handlers so we can test expression terms
        FIELD_NAMES.forEach(fieldName ->
                mapper.addHandler(new MyDbField(fieldName), handler));

        final Condition condition = mapper.apply(expressionItem);

        LOGGER.info("expressionItem: {}", expressionItem);
        LOGGER.info("condition: {}", condition);

        return condition;
    }

    private String conditionString(final String fieldName, final String value) {
        return "(" + DB_FIELD_NAME_1 + "=" + FIELD_1_VALUE + ")";
    }

    private static class MyDbField extends AbstractField {

        private final String name;

        public MyDbField(String name) {
            super(null, null, null);
            this.name = name;
        }

        @Override
        public String getType() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}