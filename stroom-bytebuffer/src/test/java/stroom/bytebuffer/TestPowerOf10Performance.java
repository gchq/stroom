package stroom.bytebuffer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks to assess the performance of getting the power of ten offset
 *
 * <pre>{@code
 * Benchmark                                            (minCapacity)   Mode  Cnt           Score   Error  Units
 * TestByteBufferPoolPerformance.getOffset_combination              5  thrpt    2  2308365816.843          ops/s
 * TestByteBufferPoolPerformance.getOffset_combination        5000000  thrpt    2   157652665.503          ops/s
 * TestByteBufferPoolPerformance.getOffset_ifElse                   5  thrpt    2  2284254869.296          ops/s
 * TestByteBufferPoolPerformance.getOffset_ifElse             5000000  thrpt    2  1145760521.782          ops/s
 * TestByteBufferPoolPerformance.getOffset_log10                    5  thrpt    2    49157867.481          ops/s
 * TestByteBufferPoolPerformance.getOffset_log10              5000000  thrpt    2    53738920.780          ops/s
 * }</pre>
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@Fork(1)
public class TestPowerOf10Performance {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    @Benchmark
    public int getOffset_log10(final MyState state) {
        return getOffset_log10(state.minCapacity);
    }

    public int getOffset_log10(final int minCapacity) {
        return (int) Math.ceil(Math.log10(minCapacity));
    }

    @Benchmark
    public int getOffset_combination(final MyState state) {
        return getOffset_combination(state.minCapacity);
    }

    public int getOffset_combination(final int minCapacity) {
        if (minCapacity <= 1) {
            return 0;
        } else if (minCapacity <= 10) {
            return 1;
        } else {
            return (int) Math.ceil(Math.log10(minCapacity));
        }
    }

    @Benchmark
    public int getOffset_ifElse(final MyState state) {
        return getOffset_ifElse(state.minCapacity);
    }

    public int getOffset_ifElse(final int minCapacity) {

        if (minCapacity <= 1) {
            return 0;
        } else if (minCapacity <= 10) {
            return 1;
        } else if (minCapacity <= 100) {
            return 2;
        } else if (minCapacity <= 1_000) {
            return 3;
        } else if (minCapacity <= 10_000) {
            return 4;
        } else if (minCapacity <= 100_000) {
            return 5;
        } else if (minCapacity <= 1_000_000) {
            return 6;
        } else if (minCapacity <= 10_000_000) {
            return 7;
        } else {
            return (int) Math.ceil(Math.log10(minCapacity));
        }
    }

    @State(Scope.Benchmark)
    public static class MyState {

        //        @Param({"5", "50", "500", "5000", "50000"})
        @Param({"5", "5000000"})
        public int minCapacity;
    }
}
