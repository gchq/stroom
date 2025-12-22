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

class TestOr extends AbstractFunctionTest<Or> {

    @Override
    Class<Or> getFunctionType() {
        return Or.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "both false",
                        ValBoolean.FALSE,
                        ValBoolean.FALSE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "one true",
                        ValBoolean.TRUE,
                        ValBoolean.FALSE,
                        ValBoolean.TRUE),
                TestCase.of(
                        "both true",
                        ValBoolean.TRUE,
                        ValBoolean.TRUE,
                        ValBoolean.TRUE));
    }
}
