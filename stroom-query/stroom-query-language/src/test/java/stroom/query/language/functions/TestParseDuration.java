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
import java.util.stream.Stream;

public class TestParseDuration extends AbstractFunctionTest<ParseDuration> {

    @Override
    Class<ParseDuration> getFunctionType() {
        return ParseDuration.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "null",
                        ValNull.INSTANCE,
                        Val.create(null)),
                TestCase.of(
                        "",
                        ValNull.INSTANCE,
                        Val.create("")),
                TestCase.of(
                        "foo",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create("foo")),
                TestCase.of(
                        "P1D foo",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create("P1D foo")),
                TestCase.of(
                        "1.1", // It is debatable whether this should parse or error
                        Val.create(Duration.ofMillis(1)),
                        Val.create(1.1D)),
                TestCase.of(
                        "1000 (long)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create(1000L)),
                TestCase.of(
                        "1000 (int)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create(1000L)),
                TestCase.of(
                        "1000 (float)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create(1000F)),
                TestCase.of(
                        "1000 (str)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create("1000")),
                TestCase.of(
                        "P2DT3H",
                        Val.create(Duration.ofDays(2).plusHours(3)),
                        Val.create("P2DT3H")),
                TestCase.of(
                        "P2D",
                        Val.create(Duration.ofDays(2)),
                        Val.create("P2D")),
                TestCase.of(
                        "1h",
                        Val.create(Duration.ofHours(1)),
                        Val.create("1h")),
                TestCase.of(
                        "22h",
                        Val.create(Duration.ofHours(22)),
                        Val.create("22h")),
                TestCase.of(
                        "5m",
                        Val.create(Duration.ofMinutes(5)),
                        Val.create("5m"))
        );
    }
}
