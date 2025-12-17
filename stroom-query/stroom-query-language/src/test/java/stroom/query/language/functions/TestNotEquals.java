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

class TestNotEquals extends AbstractEqualityFunctionTest<NotEquals> {

    @Override
    Class<NotEquals> getFunctionType() {
        return NotEquals.class;
    }

    @Override
    String getOperator() {
        return NotEquals.NAME;
    }

    @Override
    boolean addInverseTest() {
        return false;
    }

    @Override
    Stream<Values> getTestCaseValues() {
        return Stream.of(
                Values.of(2, 1, true),
                Values.of(2, 1L, true),
                Values.of(2, 1D, true),
                Values.of(2, 1F, true),
                Values.of(2, 2, false),
                Values.of(2, 2L, false),
                Values.of(2, 2D, false),
                Values.of(2, 2.0D, false),
                Values.of(2, 2F, false),
                Values.of(2, 2.0F, false),
                Values.of(2, null, true),

                Values.of(2L, 1L, true),
                Values.of(2L, 1, true),
                Values.of(2L, 1D, true),
                Values.of(2L, 1F, true),
                Values.of(2L, 2L, false),
                Values.of(2L, 2, false),
                Values.of(2L, 2D, false),
                Values.of(2L, 2.0D, false),
                Values.of(2L, 2F, false),
                Values.of(2L, 2.0F, false),
                Values.of(2L, null, true),

                Values.of(1.2D, 1.1D, true),
                Values.of(1.2D, 1, true),
                Values.of(1.2D, 1L, true),
                Values.of(1.2D, 1.1F, true),
                Values.of(1.1D, 1.1D, false),
                Values.of(1D, 1D, false),
                Values.of(1D, 1, false),
                Values.of(1D, 1L, false),
                Values.of(1.1D, 1.1F, false),
                Values.of(1.1D, null, true),

                Values.of(1.2F, 1.1F, true),
                Values.of(1.2F, 1, true),
                Values.of(1.2F, 1L, true),
                Values.of(1.2F, 1.1D, true),
                Values.of(1.1F, 1.1F, false),
                Values.of(1F, 1, false),
                Values.of(1F, 1L, false),
                Values.of(1.1F, 1.1D, false),
                Values.of(1.1F, null, true),

                Values.of(true, false, true),
                Values.of(true, true, false),
                Values.of(true, null, true),

                Values.of("dog", "cat", true),
                Values.of("CAT", "cat", true),
                Values.of("cat", "cat", false),
                Values.of("cat", null, true),

                Values.of(null, null, false)
        );
    }
}
