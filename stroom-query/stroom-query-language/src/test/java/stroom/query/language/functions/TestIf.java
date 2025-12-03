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

class TestIf extends AbstractFunctionTest<If> {

    @Override
    Class<If> getFunctionType() {
        return If.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "static 1",
                        ValLong.create(100),
                        ValBoolean.TRUE,
                        ValLong.create(100),
                        ValString.create("abc")),
                TestCase.of(
                        "static 2",
                        ValString.create("abc"),
                        ValBoolean.FALSE,
                        ValLong.create(100),
                        ValString.create("abc"))
        );
    }
}
