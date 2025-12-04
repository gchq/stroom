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

package stroom.util.concurrent;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * <pre>
 * === ONE THREAD ===
 * Benchmark                         Mode  Cnt           Score   Error  Units
 * TestAtomicInteger.atomicInteger  thrpt        633670967.338          ops/s
 * TestAtomicInteger.atomicLong     thrpt        632073002.140          ops/s
 * TestAtomicInteger.longAdder      thrpt        525088771.702          ops/s
 * TestAtomicInteger.primitiveInt   thrpt       2805891997.822          ops/s
 *
 * === TWO THREADS ===
 * Benchmark                         Mode  Cnt          Score   Error  Units
 * TestAtomicInteger.atomicInteger  thrpt       124344537.837          ops/s
 * TestAtomicInteger.atomicLong     thrpt        92389992.099          ops/s
 * TestAtomicInteger.longAdder      thrpt       933091591.699          ops/s
 * TestAtomicInteger.primitiveInt   thrpt       241382219.430          ops/s
 *
 * === TEN THREADS ===
 * Benchmark                         Mode  Cnt           Score   Error  Units
 * TestAtomicInteger.atomicInteger  thrpt         89816052.534          ops/s
 * TestAtomicInteger.atomicLong     thrpt         83321174.667          ops/s
 * TestAtomicInteger.longAdder      thrpt       4233313651.993          ops/s
 * TestAtomicInteger.primitiveInt   thrpt        188787439.079          ops/s
 * </pre>
 */
@Fork(value = 1, warmups = 1)
@BenchmarkMode(Mode.Throughput)
@Measurement(iterations = 1)
public class AtomicCountersBenchmark {

    @Threads(1)
    @Benchmark
    public void atomicLong_1(final MyAtomicLong state, final Blackhole blackhole) {
        blackhole.consume(state.atomicLong.incrementAndGet());
    }

    @Threads(1)
    @Benchmark
    public void atomicInteger_1(final MyAtomicInteger state, final Blackhole blackhole) {
        blackhole.consume(state.atomicInteger.incrementAndGet());
    }

    @Threads(1)
    @Benchmark
    public void longAdder_1(final MyLongAdder state) {
        state.longAdder.increment();
    }

    @Threads(1)
    @Benchmark
    public void primitiveInt_1(final MyInt state, final Blackhole blackhole) {
        blackhole.consume(++state.anInt);
    }

    @Threads(2)
    @Benchmark
    public void atomicLong_2(final MyAtomicLong state, final Blackhole blackhole) {
        blackhole.consume(state.atomicLong.incrementAndGet());
    }

    @Threads(2)
    @Benchmark
    public void atomicInteger_2(final MyAtomicInteger state, final Blackhole blackhole) {
        blackhole.consume(state.atomicInteger.incrementAndGet());
    }

    @Threads(2)
    @Benchmark
    public void longAdder_2(final MyLongAdder state) {
        state.longAdder.increment();
    }

    @Threads(10)
    @Benchmark
    public void atomicLong_10(final MyAtomicLong state, final Blackhole blackhole) {
        blackhole.consume(state.atomicLong.incrementAndGet());
    }

    @Threads(10)
    @Benchmark
    public void atomicInteger_10(final MyAtomicInteger state, final Blackhole blackhole) {
        blackhole.consume(state.atomicInteger.incrementAndGet());
    }

    @Threads(10)
    @Benchmark
    public void longAdder_10(final MyLongAdder state, final Blackhole blackhole) {
        state.longAdder.increment();
    }


    // --------------------------------------------------------------------------------


    @State(Scope.Benchmark)
    public static class MyAtomicLong {

        final AtomicLong atomicLong = new AtomicLong();

        @TearDown(Level.Trial)
        public void doTearDown() {
            System.out.println(atomicLong.get());
        }
    }


    // --------------------------------------------------------------------------------


    @State(Scope.Benchmark)
    public static class MyAtomicInteger {

        final AtomicInteger atomicInteger = new AtomicInteger();

        @TearDown(Level.Trial)
        public void doTearDown() {
            System.out.println(atomicInteger.get());
        }
    }


    // --------------------------------------------------------------------------------


    @State(Scope.Benchmark)
    public static class MyLongAdder {

        final LongAdder longAdder = new LongAdder();

        @TearDown(Level.Trial)
        public void doTearDown() {
            System.out.println(longAdder.sum());
        }
    }


    // --------------------------------------------------------------------------------


    @State(Scope.Benchmark)
    public static class MyInt {

        int anInt = 0;

        @TearDown(Level.Trial)
        public void doTearDown() {
            System.out.println(anInt);
        }
    }
}
