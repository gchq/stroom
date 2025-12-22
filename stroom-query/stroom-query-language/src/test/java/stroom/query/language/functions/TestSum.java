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

package stroom.query.language.functions;

import stroom.util.date.DateUtil;

import java.time.Duration;
import java.util.stream.Stream;

class TestSum extends AbstractFunctionTest<Sum> {

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
                        ValInteger.create(100))
        );
    }
}
