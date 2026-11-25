/*
 * Copyright 2021 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSum extends AbstractFunctionTest<Sum> {

    private static final Val BOOLEAN_ERROR = ValErr.create("Expected a number but got 'true'");

    @Override
    Class<Sum> getFunctionType() {
        return Sum.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "basic int",
                        ValDouble.create(111),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.ofAggregate(
                        "aggregate int",
                        ValDouble.create(111),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.of(
                        "basic double",
                        ValDouble.create(-0.8),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.ofAggregate(
                        "aggregate double",
                        ValDouble.create(-0.8),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.of(
                        "duration",
                        ValDuration.create(Duration.ofMinutes(5).toMillis()),
                        ValDuration.create(Duration.ofMinutes(3).toMillis()),
                        ValDuration.create(Duration.ofMinutes(2).toMillis())),
                TestCase.of(
                        "date",
                        ValDate.create(DateUtil.parseNormalDateTimeString("2020-10-01T00:02:00.000Z")),
                        ValDate.create(DateUtil.parseNormalDateTimeString("2020-10-01T00:00:00.000Z")),
                        ValDuration.create(Duration.ofMinutes(2).toMillis())),
                TestCase.of(
                        "null",
                        ValDouble.create(111),
                        ValInteger.create(1),
                        ValInteger.create(10),
                        ValNull.INSTANCE,
                        ValInteger.create(100)),
                // Field values are often text.
                TestCase.of(
                        "numeric text",
                        ValDouble.create(111),
                        ValString.create("1"),
                        ValString.create("100"),
                        ValInteger.create(10)),
                TestCase.ofAggregate(
                        "aggregate numeric text",
                        ValDouble.create(111),
                        ValString.create("1"),
                        ValString.create("100"),
                        ValInteger.create(10)),
                TestCase.of(
                        "booleans",
                        BOOLEAN_ERROR,
                        ValBoolean.TRUE,
                        ValBoolean.TRUE),
                TestCase.of(
                        "boolean and int",
                        BOOLEAN_ERROR,
                        ValInteger.create(1),
                        ValBoolean.TRUE,
                        ValInteger.create(2)),
                TestCase.of(
                        "null and boolean",
                        BOOLEAN_ERROR,
                        ValNull.INSTANCE,
                        ValBoolean.TRUE),
                TestCase.ofAggregate(
                        "aggregate single boolean",
                        BOOLEAN_ERROR,
                        ValBoolean.TRUE),
                TestCase.ofAggregate(
                        "aggregate booleans",
                        BOOLEAN_ERROR,
                        ValBoolean.TRUE,
                        ValBoolean.TRUE),
                TestCase.ofAggregate(
                        "aggregate boolean and int",
                        BOOLEAN_ERROR,
                        ValInteger.create(1),
                        ValBoolean.TRUE,
                        ValInteger.create(2)),
                TestCase.ofAggregate(
                        "aggregate error before boolean",
                        ValErr.create("other"),
                        ValErr.create("other"),
                        ValBoolean.TRUE)
        );
    }

    @Test
    void mergeKeepsTheError() throws ParseException {
        final Sum sum = new Sum(Sum.NAME);
        sum.setParams(new Param[]{new Ref("${val}", 0)});
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        sum.addValueReferences(valueReferenceIndex);
        final Generator generator = sum.createGenerator();

        assertThat(merge(generator, valueReferenceIndex, ValBoolean.TRUE, ValInteger.create(1)))
                .isEqualTo(BOOLEAN_ERROR);
        assertThat(merge(generator, valueReferenceIndex, ValInteger.create(1), ValBoolean.TRUE))
                .isEqualTo(BOOLEAN_ERROR);
        assertThat(merge(generator, valueReferenceIndex, ValInteger.create(1), ValInteger.create(2)))
                .isEqualTo(ValDouble.create(3));
    }

    private Val merge(final Generator generator,
                      final ValueReferenceIndex valueReferenceIndex,
                      final Val existing,
                      final Val added) {
        final StoredValues existingValues = valueReferenceIndex.createStoredValues();
        generator.set(Val.of(existing), existingValues);
        final StoredValues addedValues = valueReferenceIndex.createStoredValues();
        generator.set(Val.of(added), addedValues);
        generator.merge(existingValues, addedValues);
        return generator.eval(existingValues, null);
    }
}
