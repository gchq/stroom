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
import java.util.Collection;
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

                        final CommonExpressionMapper mapper = new CommonExpressionMapper(true);

                        final Collection<Condition> conditions = mapper.apply(expressionOperator);

                        LOGGER.info("expressionItem: {}", expressionOperator);
                        LOGGER.info("conditions: {}", conditions);

                        if (expressionOperator.op().equals(ExpressionOperator.Op.NOT)) {
                            assertThat(conditions)
                                    .contains(DSL.falseCondition());
                        } else {
                            assertThat(conditions)
                                    .isEmpty();
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

        final Collection<Condition> conditions = doTest(expressionOperator);

        assertThat(conditions)
                .isNotEmpty();
        // NOT { AND {} } == false
        assertThat(conditions).contains(DSL.falseCondition());
    }

    @Test
    void testAndEmptyAnd() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.AND)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, Collections.emptyList()))
                .build();

        final Collection<Condition> conditions = doTest(expressionOperator);

        // AND { AND {} } == true, so no condition
        assertThat(conditions)
                .isEmpty();
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

        final Collection<Condition> conditions = doTest(expressionOperator);

        // AND { 1=0, field1=123 } == false, terms condensed down to one false condition
        assertThat(conditions)
                .contains(DSL.falseCondition());
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

        final Collection<Condition> conditions = doTest(expressionOperator);

        // OR { 1=1, field1=123 } == true, terms condensed down to one true condition, so empty list
        assertThat(conditions)
                .isEmpty();
    }

    @Test
    void testNotNot() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.NOT)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.NOT, Collections.emptyList()))
                .build();

        final Collection<Condition> conditions = doTest(expressionOperator);

        // NOT { NOT {} } == true so simplifies to no conditions.
        assertThat(conditions)
                .isEmpty();
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

                    final Collection<Condition> conditions = doTest(expressionOperator);

                    assertThat(conditions)
                            .hasSize(1);
                    assertThat(conditions.iterator().next().toString())
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

                    final Collection<Condition> conditions = doTest(expressionOperator);

                    assertThat(conditions)
                            .hasSize(1);
                    // No way to inspect the conditon tree so have to do contains on toString
                    // No way to check it is an OR either
                    assertThat(conditions.iterator().next().toString())
                            .contains(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
                    assertThat(conditions.iterator().next().toString())
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

        final Collection<Condition> conditions = doTest(expressionOperator);

        assertThat(conditions)
                .hasSize(1);
        // No way to inspect the conditon tree so have to do contains on toString
        // No way to check it is an OR either
        assertThat(conditions.iterator().next().toString())
                .contains(conditionString(DB_FIELD_NAME_1, FIELD_1_VALUE));
        assertThat(conditions.iterator().next().toString())
                .contains(conditionString(DB_FIELD_NAME_2, FIELD_2_VALUE));
        assertThat(conditions.iterator().next().toString())
                .contains(conditionString(DB_FIELD_NAME_3, FIELD_3_VALUE));
    }

    private Collection<Condition> doTest(final ExpressionItem expressionItem) {
        // create a noddy term mapper that doesn't need any generated code
        Function<ExpressionTerm, Condition> handler = expressionTerm -> {
            return DSL.condition(expressionTerm.getField() + "=" + expressionTerm.getValue());
        };

        final CommonExpressionMapper mapper = new CommonExpressionMapper(false);

        // Set up some noddy term handlers so we can test expression terms
        FIELD_NAMES.forEach(fieldName ->
                mapper.addHandler(new MyDbField(fieldName), handler));

        final Collection<Condition> conditions = mapper.apply(expressionItem);

        LOGGER.info("expressionItem: {}", expressionItem);
        LOGGER.info("conditions: {}", conditions);

        return conditions;
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