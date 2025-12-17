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

class TestGreaterThan extends AbstractEqualityFunctionTest<GreaterThan> {

    @Override
    Class<GreaterThan> getFunctionType() {
        return GreaterThan.class;
    }

    @Override
    String getOperator() {
        return GreaterThan.NAME;
    }

    @Override
    Stream<Values> getTestCaseValues() {
        return Stream.of(
                Values.of(2, 1, true),
                Values.of(2, 1L, true),
                Values.of(2, 1D, true),
                Values.of(2, 1F, true),
                Values.of(2, 2, false),
                Values.of(2, null, false),

                Values.of(2L, 1L, true),
                Values.of(2L, 1, true),
                Values.of(2L, 1D, true),
                Values.of(2L, 1F, true),
                Values.of(2L, 2L, false),
                Values.of(2L, null, false),

                Values.of(1.2D, 1.1D, true),
                Values.of(1.2D, 1, true),
                Values.of(1.2D, 1L, true),
                Values.of(1.2D, 1.1F, true),
                Values.of(1.1D, 1.1D, false),
                Values.of(1.1D, null, false),

                Values.of(1.2F, 1.1F, true),
                Values.of(1.2F, 1, true),
                Values.of(1.2F, 1L, true),
                Values.of(1.2F, 1.1D, true),
                Values.of(1.1F, 1.1F, false),
                Values.of(1.1F, null, false),

                Values.of(true, false, true),
                Values.of(true, true, false),
                Values.of(true, null, false),

                Values.of(TOMORROW, TODAY, true),
                Values.of(TODAY, TODAY, false),
                Values.of(TODAY, null, false),

                Values.of("dog", "cat", true),
                Values.of("cat", "cat", false),
                Values.of("CAT", "cat", false),
                Values.of("CAT", null, false),

                // Comparing as numbers
                Values.of("10", "2", true),
                Values.of("10", "2xx", false),
                Values.of("10x", "2xx", false),
                Values.of("1.1", "1", true),
                Values.of("1.1", null, false),

                Values.of(null, null, false)
        );
    }
}
