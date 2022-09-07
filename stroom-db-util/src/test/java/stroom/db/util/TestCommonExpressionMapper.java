package stroom.db.util;


import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
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
                .collect(Collectors.toList());
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
                .contains("not((field1=123))");
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
                .collect(Collectors.toList());
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
                .collect(Collectors.toList());
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
        Function<ExpressionTerm, Condition> handler = expressionTerm ->
                DSL.condition(expressionTerm.getField() + "=" + expressionTerm.getValue());

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
