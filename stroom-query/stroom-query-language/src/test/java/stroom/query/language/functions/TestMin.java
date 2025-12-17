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

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

class TestMin extends AbstractFunctionTest<Min> {

    @Override
    Class<Min> getFunctionType() {
        return Min.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "basic int",
                        ValInteger.create(1),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.ofAggregate(
                        "aggregate int",
                        ValInteger.create(1),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.of(
                        "basic double",
                        ValDouble.create(-3.3),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.ofAggregate(
                        "aggregate double",
                        ValDouble.create(-3.3),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.of(
                        "basic durations",
                        Val.create(Duration.ofSeconds(1)),
                        Val.create(Duration.ofDays(2)),
                        Val.create(Duration.ofSeconds(1)),
                        Val.create(Duration.ofHours(3))),
                TestCase.of(
                        "basic dates",
                        Val.create(Instant.ofEpochMilli(1)),
                        Val.create(Instant.ofEpochMilli(2)),
                        Val.create(Instant.ofEpochMilli(1)),
                        Val.create(Instant.ofEpochMilli(3)))
        );
    }
}
