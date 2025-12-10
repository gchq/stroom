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

public class TestFormatDuration extends AbstractFunctionTest<FormatDuration> {

    @Override
    Class<FormatDuration> getFunctionType() {
        return FormatDuration.class;
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
                        "1h",
                        ValString.create("1h"),
                        ValDuration.create(Duration.ofHours(1).toMillis())),
                TestCase.of(
                        "22h",
                        ValString.create("22h"),
                        ValDuration.create(Duration.ofHours(22).toMillis())),
                TestCase.of(
                        "5m",
                        ValString.create("5m"),
                        ValDuration.create(Duration.ofMinutes(5).toMillis()))
        );
    }
}
