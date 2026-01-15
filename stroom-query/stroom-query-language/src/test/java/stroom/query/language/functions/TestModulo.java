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

class TestModulo extends AbstractFunctionTest<Modulo> {

    @Override
    Class<Modulo> getFunctionType() {
        return Modulo.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "100 % 30",
                        ValDouble.create(10),
                        ValInteger.create(100),
                        ValInteger.create(30)),
                TestCase.of(
                        "(100 % 30) % 4",
                        ValDouble.create(2),
                        ValInteger.create(100),
                        ValInteger.create(30),
                        ValInteger.create(4))
        );
    }
}
