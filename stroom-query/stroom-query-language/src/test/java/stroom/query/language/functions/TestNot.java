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

class TestNot extends AbstractFunctionTest<Not> {

    @Override
    Class<Not> getFunctionType() {
        return Not.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "same type 1",
                        ValBoolean.TRUE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "mixed type 1",
                        ValBoolean.TRUE,
                        ValString.create("xxx")),
                TestCase.of(
                        "same type 2",
                        ValBoolean.TRUE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "same type 2",
                        ValBoolean.FALSE,
                        ValString.create("true")),
                TestCase.of(
                        "mixed type 3",
                        ValBoolean.TRUE,
                        ValLong.create(0)),
                TestCase.of(
                        "mixed type 4",
                        ValBoolean.FALSE,
                        ValLong.create(1))
        );
    }
}
