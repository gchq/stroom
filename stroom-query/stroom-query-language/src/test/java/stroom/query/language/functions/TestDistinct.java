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

import java.util.List;
import java.util.stream.Stream;

class TestDistinct extends AbstractFunctionTest<Distinct> {

    @Override
    Class<Distinct> getFunctionType() {
        return Distinct.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "no delimiter 1",
                        ValString.create("abcdexyz"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("d"),
                                ValString.create("e"),
                                ValString.create("z"),
                                ValString.create("y"),
                                ValString.create("x")
                        )),
                TestCase.ofAggregate(
                        "no delimiter 2",
                        ValString.create("abcd"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        )),
                TestCase.ofAggregate(
                        "delim",
                        ValString.create("a, b, c, d"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create(", ")
                ),
                TestCase.ofAggregate(
                        "delim + limit",
                        ValString.create("a|b|d"),
                        List.of(
                                ValString.create("d"),
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create("|"),
                        ValInteger.create(3)
                )
        );
    }
}
