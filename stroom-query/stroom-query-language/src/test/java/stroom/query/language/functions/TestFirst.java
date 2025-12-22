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

class TestFirst extends AbstractFunctionTest<First> {

    @Override
    Class<First> getFunctionType() {
        return First.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "non-null",
                        ValLong.create(1),
                        List.of(ValLong.create(1),
                                ValLong.create(2),
                                ValLong.create(3),
                                ValLong.create(4),
                                ValLong.create(5))),
                TestCase.ofAggregate(
                        "null",
                        ValNull.INSTANCE,
                        List.of(ValNull.INSTANCE,
                                ValLong.create(2),
                                ValLong.create(3),
                                ValLong.create(4),
                                ValLong.create(5)))
        );
    }
}
