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

class TestInRange extends AbstractFunctionTest<InRange> {

    @Override
    Class<InRange> getFunctionType() {
        return InRange.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                // inRange lower and upper
                TestCase.of("value in range",
                        ValBoolean.TRUE, ValDouble.create(5),
                        ValDouble.create(1), ValDouble.create(10)),
                // equal to lower bound
                TestCase.of("value equals lower",
                        ValBoolean.TRUE, ValDouble.create(1),
                        ValDouble.create(1), ValDouble.create(10)),
                // equal to upper bound
                TestCase.of("value equals upper",
                        ValBoolean.TRUE, ValDouble.create(10),
                        ValDouble.create(1), ValDouble.create(10)),
                // below lower bound
                TestCase.of("value below lower",
                        ValBoolean.FALSE, ValDouble.create(0), ValDouble.create(1), ValDouble.create(10)),
                // above upper bound
                TestCase.of("value above upper",
                        ValBoolean.FALSE, ValDouble.create(11), ValDouble.create(1), ValDouble.create(10)),
                // non-numeric
                TestCase.of("non-numeric value",
                        ValErr.create("Parameters must be all numeric or all ISO date strings for between function"),
                        ValString.create("a"), ValDouble.create(1), ValDouble.create(10)),
                // null
                TestCase.of("null value",
                        ValErr.create("Null parameter in between function"),
                        ValNull.INSTANCE, ValDouble.create(1), ValDouble.create(10)),
                // date
                TestCase.of("date in range",
                        ValBoolean.TRUE, ValString.create("2023-01-05"),
                        ValString.create("2023-01-01"), ValString.create("2023-01-10"))
        );
    }
}
