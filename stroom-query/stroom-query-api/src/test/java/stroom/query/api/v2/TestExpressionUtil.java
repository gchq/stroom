package stroom.query.api.v2;

import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionUtil {

    public static final String FIELD = "FIELD";

    @Test
    void replaceExpressionParameters_null() {

        final ExpressionOperator expressionOperator = getExpressionOperator();

        final ExpressionOperator expressionOperator2 = ExpressionUtil.replaceExpressionParameters(
                expressionOperator,
                (List<Param>) null);

        assertThat(expressionOperator2)
                .isNotEqualTo(expressionOperator);
        final ExpressionTerm term1 = (ExpressionTerm) expressionOperator2.getChildren().get(0);
        // No params so param in query gets removed
        assertThat(term1.getValue())
                .isEqualTo("");

        assertThat(expressionOperator2.getChildren().get(1))
                .isEqualTo(expressionOperator.getChildren().get(1));
    }

    @Test
    void replaceExpressionParameters_noParams() {

        final ExpressionOperator expressionOperator = getExpressionOperator();

        final ExpressionOperator expressionOperator2 = ExpressionUtil.replaceExpressionParameters(
                expressionOperator,
                Collections.emptyList());

        assertThat(expressionOperator2)
                .isNotEqualTo(expressionOperator);
        final ExpressionTerm term = (ExpressionTerm) expressionOperator2.getChildren().get(0);
        // No params so param in query gets removed
        assertThat(term.getValue())
                .isEqualTo("");

        assertThat(expressionOperator2.getChildren().get(1))
                .isEqualTo(expressionOperator.getChildren().get(1));
    }

    @Test
    void replaceExpressionParameters_hasParams() {

        final ExpressionOperator expressionOperator = getExpressionOperator();

        final ExpressionOperator expressionOperator2 = ExpressionUtil.replaceExpressionParameters(
                expressionOperator,
                List.of(
                        Param.builder()
                                .key("foo")
                                .value("1")
                                .build(),
                        Param.builder()
                                .key("bar")
                                .value("2")
                                .build()));

        assertThat(expressionOperator2)
                .isNotEqualTo(expressionOperator);
        final ExpressionTerm term = (ExpressionTerm) expressionOperator2.getChildren().get(0);
        // No params so param in query gets removed
        assertThat(term.getValue())
                .isEqualTo("1");

        assertThat(expressionOperator2.getChildren().get(1))
                .isEqualTo(expressionOperator.getChildren().get(1));
    }

    @TestFactory
    Stream<DynamicTest> testHasTerms() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ExpressionOperator.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ExpressionUtil::hasTerms)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(ExpressionOperator.builder()
                                .enabled(false)
                                .build(),
                        false)
                .addCase(ExpressionOperator.builder()
                                .enabled(true)
                                .build(),
                        false)
                .addCase(ExpressionOperator.builder()
                                .enabled(true)
                                .addOperator(ExpressionOperator.builder()
                                        .enabled(true)
                                        .build())
                                .build(),
                        false)
                .addCase(ExpressionOperator.builder()
                                .enabled(true)
                                .addTerm(ExpressionTerm.builder()
                                        .enabled(false)
                                        .build())
                                .build(),
                        false)
                .addCase(ExpressionOperator.builder()
                                .enabled(true)
                                .addTerm(ExpressionTerm.builder()
                                        .enabled(false)
                                        .build())
                                .addTerm(ExpressionTerm.builder()
                                        .enabled(true)
                                        .build())
                                .build(),
                        true)
                .build();
    }

    @Test
    void testCopyOperator() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .enabled(true)
                .addTerm(ExpressionTerm.builder()
                        .field("foo")
                        .enabled(false)
                        .condition(Condition.EQUALS)
                        .value("123")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .enabled(false)
                        .field("bar")
                        .condition(Condition.NOT_EQUALS)
                        .value("456")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .enabled(true)
                        .addTerm(ExpressionTerm.builder()
                                .field("apple")
                                .enabled(false)
                                .condition(Condition.EQUALS)
                                .value("789")
                                .build())
                        .addTerm(ExpressionTerm.builder()
                                .enabled(false)
                                .field("orange")
                                .condition(Condition.NOT_EQUALS)
                                .value("789")
                                .build())
                        .build())
                .build();

        final ExpressionOperator operator2 = ExpressionUtil.copyOperator(operator);
        assertThat(operator2)
                .isEqualTo(operator);
        assertThat(operator2)
                .isNotSameAs(operator);
    }

    @Test
    void testCopyOperator_withFilter() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .enabled(true)
                .addTerm(ExpressionTerm.builder()
                        .field("foo")
                        .enabled(false)
                        .condition(Condition.EQUALS)
                        .value("123")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .enabled(false)
                        .field("bar")
                        .condition(Condition.NOT_EQUALS)
                        .value("456")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .enabled(true)
                        .addTerm(ExpressionTerm.builder()
                                .field("apple")
                                .enabled(false)
                                .condition(Condition.EQUALS)
                                .value("789")
                                .build())
                        .addTerm(ExpressionTerm.builder()
                                .enabled(false)
                                .field("orange")
                                .condition(Condition.NOT_EQUALS)
                                .value("789")
                                .build())
                        .build())
                .build();

        final ExpressionOperator operator2 = ExpressionUtil.copyOperator(operator, item ->
                !(item instanceof ExpressionTerm term && term.getField().equals("foo")));

        assertThat(operator2)
                .isNotEqualTo(operator);
        assertThat(operator2)
                .isNotSameAs(operator);

        assertThat(operator.containsField("foo", "bar", "apple", "orange"))
                .isTrue();

        // foo has gone
        assertThat(operator2.containsField("foo"))
                .isFalse();
        assertThat(operator2.containsField("bar", "apple", "orange"))
                .isTrue();
    }

    private static ExpressionOperator getExpressionOperator() {
        return ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder()
                        .field(FIELD)
                        .condition(Condition.EQUALS)
                        .value("${foo}")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field(FIELD)
                        .condition(Condition.EQUALS)
                        .value("bar")
                        .build())
                .build();
    }
}
