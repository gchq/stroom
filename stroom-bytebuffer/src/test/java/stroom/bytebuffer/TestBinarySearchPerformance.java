package stroom.bytebuffer;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import org.junit.jupiter.api.Test;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
//@Warmup(iterations = 2)
@Warmup(iterations = 2, time = 2_000, timeUnit = TimeUnit.MILLISECONDS)
//@Measurement(iterations = 2)
@Measurement(iterations = 2, time = 2_000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class TestBinarySearchPerformance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBinarySearchPerformance.class);

    private static final int[] CAPACITIES = {
            1_024,
            2_048,
            4_096,
            8_192,
            16_384,
            32_768,
            65_536};
    private static final int COUNT = CAPACITIES.length;

    private static final Int2ObjectAVLTreeMap<FakeQueue> TREE_MAP = new Int2ObjectAVLTreeMap<>();
    private static final FakeQueue[] QUEUES = new FakeQueue[COUNT];

    static {
        for (int i = 0, capacitiesLength = CAPACITIES.length; i < capacitiesLength; i++) {
            final int capacity = CAPACITIES[i];
            final FakeQueue queue = new FakeQueue(capacity);
            TREE_MAP.put(capacity, queue);
            QUEUES[i] = queue;
        }
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    @Test
    void test() {
        List.of(1022, 1024, 1025, 67_000).forEach(cap -> {

            int idx = Arrays.binarySearch(CAPACITIES, cap);
            final FakeQueue queue;
            if (idx >= 0) {
                queue = QUEUES[idx];
            } else {
                idx = -1 * (idx + 1);
                if (idx >= COUNT) {
                    queue = null;
                } else {
                    queue = QUEUES[idx];
                }
            }
            LOGGER.info("result: {}", NullSafe.get(queue, FakeQueue::capacity));
        });
    }

    @Test
    void test2() {
        List.of(1022, 1024, 1025, 67_000).forEach(cap -> {
            final Integer result = NullSafe.get(TREE_MAP.tailMap((int) cap).firstEntry(),
                    Entry::getValue,
                    queue -> queue.capacity);
            LOGGER.info("cap: {}, result: {}", cap, result);
        });
    }

    @Benchmark
    public void treeMap(final MyState state, final Blackhole blackhole) {
        final Entry<Integer, FakeQueue> entry = TREE_MAP.tailMap(state.capacity).firstEntry();
        final FakeQueue queue;
        if (entry != null) {
            queue = entry.getValue();
        } else {
            queue = null;
        }
        blackhole.consume(queue);
    }

    @Benchmark
    public void binSearch(final MyState state, final Blackhole blackhole) {
        int idx = Arrays.binarySearch(CAPACITIES, state.capacity);
        final FakeQueue queue;
        if (idx >= 0) {
            queue = QUEUES[idx];
        } else {
            idx = -1 * (idx + 1);
            if (idx >= COUNT) {
                queue = null;
            } else {
                queue = QUEUES[idx];
            }
        }
        blackhole.consume(queue);
    }


    // --------------------------------------------------------------------------------


    @State(Scope.Benchmark)
    public static class MyState {

        //                @Param({"10", "100", "1000", "10000", "1000000000"})
//        @Param({"10"})
        @Param({"1022", "1024", "1025", "5000", "17000", "64000"})
        public int capacity;
    }


    // --------------------------------------------------------------------------------


    private static record FakeQueue(int capacity) {

    }
}
