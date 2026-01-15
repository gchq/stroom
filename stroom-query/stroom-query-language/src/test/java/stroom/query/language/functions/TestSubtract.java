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

class TestSubtract extends AbstractFunctionTest<Subtract> {

    @Override
    Class<Subtract> getFunctionType() {
        return Subtract.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "Numeric 1",
                        ValDouble.create(2),
                        ValLong.create(5),
                        ValLong.create(3)),
                TestCase.of(
                        "Numeric 2",
                        ValDouble.create(0),
                        ValLong.create(5),
                        ValLong.create(5)),
                TestCase.of(
                        "Numeric 3",
                        ValDouble.create(-3),
                        ValLong.create(2),
                        ValLong.create(5)),
                // Subtract is a numeric function so subtraction of dates gives an error
                TestCase.of(
                        "2",
                        ValErr.INSTANCE,
                        ValString.create("2008-11-18T09:47:50.500"),
                        ValString.create("2008-11-18T09:47:50.100")),
                TestCase.of(
                        "2",
                        ValErr.INSTANCE,
                        ValString.create("2008-11-18T09:47:50.548Z"),
                        ValString.create("2008-11-18T09:47:50.548Z")),
                TestCase.of(
                        "duration",
                        ValDuration.create(Duration.ofMinutes(5).toMillis()),
                        ValDuration.create(Duration.ofMinutes(7).toMillis()),
                        ValDuration.create(Duration.ofMinutes(2).toMillis())),
                TestCase.of(
                        "date",
                        ValDate.create(stroom.util.date.DateUtil.parseNormalDateTimeString("2020-10-01T00:02:00.000Z")),
                        ValDate.create(DateUtil.parseNormalDateTimeString("2020-10-01T00:04:00.000Z")),
                        ValDuration.create(Duration.ofMinutes(2).toMillis()))
        );
    }
}
