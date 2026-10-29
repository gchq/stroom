/*
 * Copyright 2026 Crown Copyright
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

class TestShannonEntropy extends AbstractFunctionTest<ShannonEntropy> {

    @Override
    Class<ShannonEntropy> getFunctionType() {
        return ShannonEntropy.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "empty",
                        ValDouble.create(0.0),
                        ValString.create("")),
                TestCase.of(
                        "singleCharacter",
                        ValDouble.create(0.0),
                        ValString.create("aaaaaaaa")),
                TestCase.of(
                        "password",
                        ValDouble.create(2.75),
                        ValString.create("password")),
                TestCase.of(
                        "random",
                        ValDouble.create(3.5850),
                        ValString.create("xkq7zp2vbnwr")),
                TestCase.of(
                        "base64",
                        ValDouble.create(4.3712),
                        ValString.create("aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQ=")),
                TestCase.of(
                        "number",
                        ValDouble.create(2.3219),
                        ValInteger.create(12345)),
                // A static null must be passed through, not measured (StringLength's static path throws an NPE)
                TestCase.of(
                        "null",
                        ValNull.INSTANCE,
                        ValNull.INSTANCE),
                TestCase.of(
                        "error",
                        ValErr.create("Some error"),
                        ValErr.create("Some error")));
    }
}
