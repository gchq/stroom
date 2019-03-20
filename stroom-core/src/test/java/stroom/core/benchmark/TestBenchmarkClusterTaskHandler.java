/*
 * Copyright 2016 Crown Copyright
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

package stroom.core.benchmark;


import org.junit.jupiter.api.Test;
import stroom.core.benchmark.BenchmarkClusterConfig;
import stroom.core.benchmark.BenchmarkClusterExecutor;
import stroom.util.shared.Period;
import stroom.test.common.util.test.StroomUnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class TestBenchmarkClusterTaskHandler extends StroomUnitTest {
    @Test
    void testSimple() {
        final BenchmarkClusterExecutor benchmarkClusterTaskHandler = new BenchmarkClusterExecutor(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BenchmarkClusterConfig());
        assertThat(benchmarkClusterTaskHandler.toEPS(1000, new Period(0L, 1000L))).as("1000 EPS").isEqualTo(1000);
        assertThat(benchmarkClusterTaskHandler.toEPS(1000000L, new Period(0L, 1000L))).as("1000000 EPS").isEqualTo(1000000);
    }
}
