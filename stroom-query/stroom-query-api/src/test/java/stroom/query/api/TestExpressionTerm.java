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
                    final String field = testCase.getInput()._1;
                    final ExpressionTerm expressionTerm = testCase.getInput()._2;
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
                .withWrappedInputType(new TypeLiteral<Tuple2<Predicate<ExpressionTerm>, ExpressionTerm>>() {
                })
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final Predicate<ExpressionTerm> predicate = testCase.getInput()._1;
                    final ExpressionTerm expressionTerm = testCase.getInput()._2;
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
