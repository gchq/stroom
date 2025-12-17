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

package stroom.query.api;

import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionOperator {

    @TestFactory
    Stream<DynamicTest> testContainsField() {

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, ExpressionOperator.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final String field = testCase.getInput()._1;
                    final ExpressionOperator expressionItem = testCase.getInput()._2;
                    return expressionItem.containsField(field);
                })
                .withSimpleEqualityAssertion()

                .addCase(Tuple.of("foo", ExpressionOperator.builder()
                        .op(Op.NOT)
                        .build()), false)

                .addCase(Tuple.of("foo", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder()
                                .field("bar")
                                .condition(Condition.EQUALS)
                                .value("123")
                                .build())
                        .build()), false)

                .addCase(Tuple.of(null, ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("foo").build())
                        .build()), false)

                .addCase(Tuple.of("foo", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("foo").build())
                        .build()), true)

                .addCase(Tuple.of("foo", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("bar").build())
                        .addTerm(ExpressionTerm.builder().field("foo").build())
                        .build()), true)

                .addCase(Tuple.of("foo", ExpressionOperator.builder()
                        .addOperator(ExpressionOperator.builder()
                                .addOperator(ExpressionOperator.builder()
                                        .addTerm(ExpressionTerm.builder().field("foo").build())
                                        .build())
                                .build())
                        .build()), true)

                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsField_multiple() {

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class, ExpressionOperator.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final String field1 = testCase.getInput()._1;
                    final String field2 = testCase.getInput()._2;
                    final ExpressionOperator expressionItem = testCase.getInput()._3;
                    return expressionItem.containsField(field1, field2);
                })
                .withSimpleEqualityAssertion()

                .addCase(Tuple.of("apple", "pear", ExpressionOperator.builder()
                        .op(Op.NOT)
                        .build()), false)

                .addCase(Tuple.of("apple", "pear", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder()
                                .field("foo")
                                .condition(Condition.EQUALS)
                                .value("123")
                                .build())
                        .build()), false)

                .addCase(Tuple.of("apple", "pear", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("apple").build())
                        .build()), true)

                .addCase(Tuple.of("apple", "pear", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("pear").build())
                        .build()), true)

                .addCase(Tuple.of("apple", "pear", ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("apple").build())
                        .addTerm(ExpressionTerm.builder().field("pear").build())
                        .build()), true)

                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsTerm() {

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Predicate<ExpressionTerm>, ExpressionOperator>>() {
                })
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final Predicate<ExpressionTerm> predicate = testCase.getInput()._1;
                    final ExpressionOperator expressionItem = testCase.getInput()._2;
                    return expressionItem.containsTerm(predicate);
                })
                .withSimpleEqualityAssertion()

                .addCase(Tuple.of(
                                term -> term.getField().equals("foo"),
                                ExpressionOperator.builder()
                                        .op(Op.NOT)
                                        .build()),
                        false)

                .addCase(Tuple.of(
                                term -> term.getField().equals("foo"),
                                ExpressionOperator.builder()
                                        .addTerm(ExpressionTerm.builder()
                                                .field("bar")
                                                .condition(Condition.EQUALS)
                                                .value("123")
                                                .build())
                                        .build()),
                        false)

                .addCase(Tuple.of(
                                term -> term.getField().equals("foo") && term.getCondition().equals(Condition.EQUALS),
                                ExpressionOperator.builder()
                                        .addTerm(ExpressionTerm.builder()
                                                .field("foo")
                                                .condition(Condition.EQUALS)
                                                .value("123")
                                                .build())
                                        .build()),
                        true)

                .addCase(Tuple.of(
                                term -> term.getField().equals("foo"),
                                ExpressionOperator.builder()
                                        .addOperator(ExpressionOperator.builder()
                                                .addOperator(ExpressionOperator.builder()
                                                        .addTerm(ExpressionTerm.builder().field("foo").build())
                                                        .build())
                                                .build())
                                        .build()),
                        true)

                .build();
    }

    @Test
    void testHasEnabledChildren_noChildren() {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .build();

        assertThat(expressionOperator.hasEnabledChildren())
                .isFalse();
        assertThat(expressionOperator.getEnabledChildren())
                .isEmpty();
    }

    @Test
    void testHasEnabledChildren_oneEnabledChild() {
        final ExpressionTerm term = ExpressionTerm.builder().field("foo").build();
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(term)
                .build();

        assertThat(expressionOperator.hasEnabledChildren())
                .isTrue();
        assertThat(expressionOperator.getEnabledChildren())
                .containsExactly(term);
    }

    @Test
    void testHasEnabledChildren_oneDisabled() {
        final ExpressionTerm term = ExpressionTerm.builder().field("foo").enabled(false).build();
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(term)
                .build();

        assertThat(expressionOperator.hasEnabledChildren())
                .isFalse();
        assertThat(expressionOperator.getEnabledChildren())
                .isEmpty();
    }

    @Test
    void testHasEnabledChildren_oneDisabledOneEnabled() {
        final ExpressionTerm term1 = ExpressionTerm.builder().field("foo").enabled(false).build();
        final ExpressionTerm term2 = ExpressionTerm.builder().field("bar").enabled(true).build();
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(term1)
                .addTerm(term2)
                .build();

        assertThat(expressionOperator.hasEnabledChildren())
                .isTrue();
        assertThat(expressionOperator.getEnabledChildren())
                .containsExactly(term2);
    }
}
