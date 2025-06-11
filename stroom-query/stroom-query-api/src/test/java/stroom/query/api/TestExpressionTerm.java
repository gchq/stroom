package stroom.query.api;

import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.function.Predicate;
import java.util.stream.Stream;

class TestExpressionTerm {

    @TestFactory
    Stream<DynamicTest> testContainsField() {

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, ExpressionTerm.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final var field = testCase.getInput()._1;
                    final var expressionTerm = testCase.getInput()._2;
                    return expressionTerm.containsField(field);
                })
                .withSimpleEqualityAssertion()

                .addCase(Tuple.of(
                                null,
                                ExpressionTerm.builder()
                                        .field("bar")
                                        .condition(Condition.EQUALS)
                                        .value("123")
                                        .build()),
                        false)

                .addCase(Tuple.of(
                        "foo",
                                ExpressionTerm.builder()
                                        .field("bar")
                                        .condition(Condition.EQUALS)
                                        .value("123")
                                        .build()),
                        false)

                .addCase(Tuple.of(
                                "foo",
                                ExpressionTerm.builder()
                                        .field("foo")
                                        .condition(Condition.EQUALS)
                                        .value("123")
                                        .build()),
                        true)

                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsTerm() {

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Predicate<ExpressionTerm>, ExpressionTerm>>(){})
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final var predicate = testCase.getInput()._1;
                    final var expressionTerm = testCase.getInput()._2;
                    return expressionTerm.containsTerm(predicate);
                })
                .withSimpleEqualityAssertion()

                .addCase(Tuple.of(
                                term -> term.getField().equals("foo"),
                                ExpressionTerm.builder()
                                        .field("bar")
                                        .condition(Condition.EQUALS)
                                        .value("123")
                                        .build()),
                        false)

                .addCase(Tuple.of(
                                term -> term.getField().equals("foo"),
                                ExpressionTerm.builder()
                                        .field("foo")
                                        .condition(Condition.EQUALS)
                                        .value("123")
                                        .build()),
                        true)

                .build();
    }
}
