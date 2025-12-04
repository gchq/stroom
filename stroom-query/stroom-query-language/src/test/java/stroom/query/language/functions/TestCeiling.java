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

import java.util.stream.Stream;

class TestCeiling extends AbstractFunctionTest<Ceiling> {

    @Override
    Class<Ceiling> getFunctionType() {
        return Ceiling.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "whole 1",
                        ValDouble.create(9),
                        ValDouble.create(8.1234)),
                TestCase.of(
                        "whole 2",
                        ValDouble.create(9),
                        ValDouble.create(8.9234)),
                TestCase.of(
                        "decimal 1",
                        ValDouble.create(1.224),
                        ValDouble.create(1.22345),
                        ValInteger.create(3))
        );
    }
}
