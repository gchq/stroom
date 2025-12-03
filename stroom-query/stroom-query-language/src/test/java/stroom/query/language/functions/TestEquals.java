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

import java.time.Duration;
import java.util.stream.Stream;

class TestEquals extends AbstractEqualityFunctionTest<Equals> {

    @Override
    Class<Equals> getFunctionType() {
        return Equals.class;
    }

    @Override
    String getOperator() {
        return Equals.NAME;
    }

    @Override
    boolean addInverseTest() {
        return false;
    }

    @Override
    Stream<Values> getTestCaseValues() {
        return Stream.of(
                Values.of(2, 1, false),
                Values.of(2, 1L, false),
                Values.of(2, 1D, false),
                Values.of(2, 1F, false),
                Values.of(2, 2, true),
                Values.of(2, 2L, true),
                Values.of(2, 2D, true),
                Values.of(2, 2.0D, true),
                Values.of(2, 2F, true),
                Values.of(2, 2.0F, true),
                Values.of(2, null, false),

                Values.of(2L, 1L, false),
                Values.of(2L, 1, false),
                Values.of(2L, 1D, false),
                Values.of(2L, 1F, false),
                Values.of(2L, 2L, true),
                Values.of(2L, 2, true),
                Values.of(2L, 2D, true),
                Values.of(2L, 2.0D, true),
                Values.of(2L, 2F, true),
                Values.of(2L, 2.0F, true),
                Values.of(2L, null, false),

                Values.of(1.2D, 1.1D, false),
                Values.of(1.2D, 1, false),
                Values.of(1.2D, 1L, false),
                Values.of(1.2D, 1.1F, false),
                Values.of(1.1D, 1.1D, true),
                Values.of(1D, 1D, true),
                Values.of(1D, 1, true),
                Values.of(1D, 1L, true),
                Values.of(1.1D, 1.1F, true),
                Values.of(1.1D, null, false),

                Values.of(1.2F, 1.1F, false),
                Values.of(1.2F, 1, false),
                Values.of(1.2F, 1L, false),
                Values.of(1.2F, 1.1D, false),
                Values.of(1.1F, 1.1F, true),
                Values.of(1F, 1, true),
                Values.of(1F, 1L, true),
                Values.of(1.1F, 1.1D, true),
                Values.of(1.1F, null, false),

                Values.of(true, false, false),
                Values.of(true, true, true),
                Values.of(true, null, false),

                Values.of("dog", "cat", false),
                Values.of("CAT", "cat", false),
                Values.of("cat", "cat", true),
                Values.of("cat", null, false),

                Values.of("1", "1", true),
                Values.of("1", "2", false),
                Values.of("1", null, false),

                Values.of(true, "true", true),
                Values.of(true, 1, true),
                Values.of(true, 1L, true),
                Values.of(true, 1F, true),
                Values.of(true, 1.0F, true),
                Values.of(true, 1D, true),
                Values.of(true, 1.0D, true),
                Values.of(true, null, false),

                Values.of(false, "false", true),
                Values.of(false, 0, true),
                Values.of(false, 0L, true),
                Values.of(false, 0F, true),
                Values.of(false, 0.0F, true),
                Values.of(false, 0.0D, true),
                Values.of(false, null, false),

                Values.of(Duration.ofSeconds(2), Duration.ofSeconds(1), false),
                Values.of(Duration.ofSeconds(1), Duration.ofSeconds(1), true),
                Values.of(Duration.ofSeconds(1), 1_000, true),

                Values.of(null, null, true)
        );
    }
}
