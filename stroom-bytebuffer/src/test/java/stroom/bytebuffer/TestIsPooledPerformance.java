package stroom.bytebuffer;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark                               (capacity)   Mode  Cnt           Score   Error  Units
 * TestIsPooledPerformance.getOffsetExact          10  thrpt    2  1901489004.173          ops/s
 * TestIsPooledPerformance.getOffsetExact      100000  thrpt    2  1932759579.317          ops/s
 * TestIsPooledPerformance.getOffsetExact  1000000000  thrpt    2  1938307698.827          ops/s
 * TestIsPooledPerformance.getWithMap              10  thrpt    2   356849775.114          ops/s
 * TestIsPooledPerformance.getWithMap          100000  thrpt    2   379429938.054          ops/s
 * TestIsPooledPerformance.getWithMap      1000000000  thrpt    2   380247575.973          ops/s
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@Fork(1)
public class TestIsPooledPerformance {

    private static final Object[] POOL = new Object[10];
    private static final Int2IntMap CAPACITY_TO_OFFSET_MAP = new Int2IntArrayMap();

    static {
        for (int offset = 1; offset < 9; offset++) {
            // Leave a gap at offset 5 which is 100_000
            if (offset != 5) {
                CAPACITY_TO_OFFSET_MAP.put((int) Math.pow(10, offset), offset);
                POOL[offset] = new Object();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    @Benchmark
    public void getOffsetExact(final MyState state, final Blackhole blackhole) {
        final Integer offsetExact = ByteBufferPoolImpl8.getOffsetExact(state.capacity);
        final boolean result;
        if (offsetExact != null) {
            final Object obj = POOL[offsetExact];
            result = obj != null;
        } else {
            result = false;
        }
        blackhole.consume(result);
    }

    @Benchmark
    public void getWithMap(final MyState state, final Blackhole blackhole) {
        final int offset = CAPACITY_TO_OFFSET_MAP.get((int) state.capacity);
        final boolean result = offset > 0;
        blackhole.consume(result);
    }

    @State(Scope.Benchmark)
    public static class MyState {

        //                @Param({"10", "100", "1000", "10000", "1000000000"})
//        @Param({"10"})
        @Param({"10", "100000", "1000000000"})
        public Integer capacity;
    }
}
