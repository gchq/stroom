/*
 * Copyright 2022 Crown Copyright
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
import stroom.util.json.JsonUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void testSerialisation_withoutOptions_unchanged() {
        final ExpressionTerm term = ExpressionTerm.builder()
                .field("user")
                .condition(Condition.EQUALS)
                .value("admin*")
                .options(new HashMap<>())
                .build();

        assertThat(term.getOptions())
                .isNull();
        assertThat(JsonUtil.writeValueAsString(term, false))
                .isEqualTo("{\"type\":\"term\",\"field\":\"user\",\"condition\":\"EQUALS\",\"value\":\"admin*\"}");
    }

    @Test
    void testSerialisation_withOptions() {
        final ExpressionTerm term = ExpressionTerm.builder()
                .field("user")
                .condition(Condition.EQUALS)
                .value("admin*")
                .options(Map.of("optionA", "true"))
                .build();

        final String json = JsonUtil.writeValueAsString(term, false);
        assertThat(json)
                .contains("\"options\":{\"optionA\":\"true\"}");

        final ExpressionTerm term2 = JsonUtil.readValue(json, ExpressionTerm.class);
        assertThat(term2)
                .isEqualTo(term);
        assertThat(term2.getOptions())
                .containsExactly(Map.entry("optionA", "true"));
    }

    @Test
    void testDeserialisation_legacyJsonHasNoOptions() {
        final ExpressionTerm term = JsonUtil.readValue(
                "{\"type\":\"term\",\"field\":\"user\",\"condition\":\"EQUALS\",\"value\":\"bob\"}",
                ExpressionTerm.class);

        assertThat(term.getOptions())
                .isNull();
    }

    @Test
    void testEqualsAndCopy_includeOptions() {
        final ExpressionTerm term = ExpressionTerm.builder()
                .field("user")
                .condition(Condition.EQUALS)
                .value("bob")
                .options(Map.of("optionB", "1"))
                .build();
        final ExpressionTerm noOptions = ExpressionTerm.builder()
                .field("user")
                .condition(Condition.EQUALS)
                .value("bob")
                .build();

        assertThat(term)
                .isNotEqualTo(noOptions);
        assertThat(term.copy().build())
                .isEqualTo(term);
        assertThat(term.copy().build().hashCode())
                .isEqualTo(term.hashCode());
    }

    @Test
    void testOptions_areImmutable() {
        final Map<String, String> options = new HashMap<>();
        options.put("optionA", "true");
        final ExpressionTerm term = ExpressionTerm.builder()
                .field("user")
                .condition(Condition.EQUALS)
                .value("bob")
                .options(options)
                .build();

        // Changing the source map doesn't change the term
        options.put("optionB", "1");
        assertThat(term.getOptions())
                .containsOnlyKeys("optionA");
        assertThatThrownBy(() -> term.getOptions().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
