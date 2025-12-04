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

package stroom.util.shared.string;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

public class TestCIKeyPerf {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCIKeyPerf.class);

    private static final int FORK_COUNT = 1;
    private static final int FORK_WARMUPS = 1;
    private static final int ITERATIONS = 1;
    private static final int THREADS = 10;

    @Threads(THREADS)
    @Fork(value = FORK_COUNT, warmups = FORK_WARMUPS)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Measurement(iterations = ITERATIONS)
    public void getUnknownCIKey(final Blackhole blackhole) {
        final CIKey ciKey = CIKey.of("Food");
        blackhole.consume(ciKey);
    }

    @Threads(THREADS)
    @Fork(value = FORK_COUNT, warmups = FORK_WARMUPS)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Measurement(iterations = ITERATIONS)
    public void getKnownCIKey(final Blackhole blackhole) {
        final CIKey ciKey = CIKey.of("Feed");
        blackhole.consume(ciKey);
    }

    @Threads(THREADS)
    @Fork(value = FORK_COUNT, warmups = FORK_WARMUPS)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Measurement(iterations = ITERATIONS)
    public void getDynamicCIKey(final Blackhole blackhole) {
        final CIKey ciKey = CIKey.ofDynamicKey("Fume");
        blackhole.consume(ciKey);
    }

    @Threads(THREADS)
    @Fork(value = FORK_COUNT, warmups = FORK_WARMUPS)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Measurement(iterations = ITERATIONS)
    public void getUnknownLowerCIKey(final Blackhole blackhole) {
        final CIKey ciKey = CIKey.ofLowerCase("feet");
        blackhole.consume(ciKey);
    }
}
