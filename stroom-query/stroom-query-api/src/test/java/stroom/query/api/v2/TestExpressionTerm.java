package stroom.query.api.v2;

import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

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
}
