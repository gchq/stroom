package stroom.db.util;


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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestCommonExpressionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCommonExpressionMapper.class);

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
                        .field("xxx")
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value("123").build())
                .build();

        final Collection<Condition> conditions = doTest(expressionOperator);

        // AND { 1=0, xxx=123 } == false, terms condensed down to one false condition
        assertThat(conditions)
                .contains(DSL.falseCondition());
    }

    @Test
    void testAlwaysTrueOr() {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(
                true, ExpressionOperator.Op.OR)
                .addOperator(new ExpressionOperator(true, ExpressionOperator.Op.AND, Collections.emptyList()))
                .addTerm(new ExpressionTerm.Builder()
                        .field("xxx")
                        .condition(ExpressionTerm.Condition.EQUALS)
                        .value("123").build())
                .build();

        final Collection<Condition> conditions = doTest(expressionOperator);

        // OR { 1=1, xxx=123 } == true, terms condensed down to one true condition, so empty list
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

        // OR { 1=1, xxx=123 } == true, terms condensed down to one true condition, so empty list
        assertThat(conditions)
                .isEmpty();
    }

    private Collection<Condition> doTest(final ExpressionItem expressionItem) {
        final CommonExpressionMapper mapper = new CommonExpressionMapper(true);

        final Collection<Condition> conditions = mapper.apply(expressionItem);

        LOGGER.info("expressionItem: {}", expressionItem);
        LOGGER.info("conditions: {}", conditions);

        return conditions;
    }
}