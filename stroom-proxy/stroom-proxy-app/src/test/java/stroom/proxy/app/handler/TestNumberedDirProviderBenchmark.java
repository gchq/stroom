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

package stroom.proxy.app.handler;

import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.function.LongFunction;

public class TestNumberedDirProviderBenchmark {

    @Fork(value = 1, warmups = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Measurement(iterations = 1)
    public void benchProviderMethod1(final ExecutionPlan plan, final Blackhole blackhole) {
        final LongFunction<String> function = plan.func;
        blackhole.consume(function.apply(plan.input));
    }

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        @Param({"5", "500000000"})
        public long input;

        @Param({"1", "3"})
        public int methodNo;

        public LongFunction<String> func;

        @Setup(Level.Invocation)
        public void setUp() {
            if (methodNo == 1) {
                func = ExecutionPlan::create1;
            } else if (methodNo == 2) {
                func = ExecutionPlan::create2;
            } else if (methodNo == 3) {
                func = ExecutionPlan::create3;
            } else {
                throw new RuntimeException(LogUtil.message("Bad methodNo {}", methodNo));
            }
        }

        private static String create1(final long num) {
            return Strings.padStart(Long.toString(num), 10, '0');
        }

        private static String create2(final long num) {
            if (num == 0) {
                return "0000000000";
            } else {
                final int length = (int) (Math.log10(num) + 1);
                return switch (length) {
                    case 0 -> "0000000000";
                    case 1 -> "000000000" + num;
                    case 2 -> "00000000" + num;
                    case 3 -> "0000000" + num;
                    case 4 -> "000000" + num;
                    case 5 -> "00000" + num;
                    case 6 -> "0000" + num;
                    case 7 -> "000" + num;
                    case 8 -> "00" + num;
                    case 9 -> "0" + num;
                    case 10 -> "" + num;
                    default -> throw new IllegalArgumentException("num is too big");
                };
            }
        }

        private static String create3(final long num) {
            if (num == 0) {
                return "0000000000";
            } else {
                final String str = String.valueOf(num);
                final int len = str.length();
                return switch (len) {
                    case 0 -> "0000000000";
                    case 1 -> "000000000" + str;
                    case 2 -> "00000000" + str;
                    case 3 -> "0000000" + str;
                    case 4 -> "000000" + str;
                    case 5 -> "00000" + str;
                    case 6 -> "0000" + str;
                    case 7 -> "000" + str;
                    case 8 -> "00" + str;
                    case 9 -> "0" + str;
                    default -> str;
                };
            }
        }
    }
}
