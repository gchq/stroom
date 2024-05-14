package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferFactory;
import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.bytebuffer.ByteBufferPoolImpl10;
import stroom.bytebuffer.ByteBufferPoolImpl8;
import stroom.bytebuffer.ByteBufferPoolImpl9;
import stroom.util.functions.TriConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 *
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
//@Warmup(iterations = 2, time = 10, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2)
//@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class TestByteBufferFactoryPerformance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestByteBufferFactoryPerformance.class);

    private static final ByteBufferPoolConfig IMPL9_CONFIG = new ByteBufferPoolConfig().withPooledByteBufferCounts(
            Map.ofEntries(
                    Map.entry(4, 1_000),
                    Map.entry(8, 1_000),
                    Map.entry(16, 1_000),
                    Map.entry(32, 1_000),
                    Map.entry(64, 1_000),
                    Map.entry(128, 1_000),
                    Map.entry(256, 1_000),
                    Map.entry(512, 1_000),
                    Map.entry(1_024, 1_000),
                    Map.entry(2_048, 1_000),
                    Map.entry(4_096, 1_000),
                    Map.entry(8_192, 1_000),
                    Map.entry(16_384, 1_000),
                    Map.entry(32_768, 1_000),
                    Map.entry(65_536, 1_000)));

    private static final ByteBufferPoolConfig IMPL8_CONFIG = new ByteBufferPoolConfig().withPooledByteBufferCounts(
            Map.ofEntries(
                    Map.entry(10, 1_000),
                    Map.entry(100, 1_000),
                    Map.entry(1_000, 1_000),
                    Map.entry(10_000, 1_000),
                    Map.entry(100_000, 1_000)));

    private static final ByteBufferPoolConfig IMPL10_CONFIG = new ByteBufferPoolConfig().withPooledByteBufferCounts(
            Map.ofEntries(
                    Map.entry(4, 1_000),
                    Map.entry(8, 1_000),
                    Map.entry(16, 1_000),
                    Map.entry(32, 1_000),
                    Map.entry(64, 1_000),
                    Map.entry(128, 1_000),
                    Map.entry(256, 1_000),
                    Map.entry(512, 1_000),
                    Map.entry(1_024, 1_000),
                    Map.entry(2_048, 1_000),
                    Map.entry(4_096, 1_000),
                    Map.entry(8_192, 1_000),
                    Map.entry(16_384, 1_000),
                    Map.entry(32_768, 1_000),
                    Map.entry(65_536, 1_000)));

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

//    @Threads(Threads.MAX)
//    @Benchmark
//    public void benchmark(final MyState state, final Blackhole blackhole) {
//
//        ByteBuffer byteBuffer = null;
//        try {
//            byteBuffer = state.byteBufferFactory.acquire(state.getNextCapacity());
//
//            blackhole.consume(byteBuffer);
//        } finally {
//            if (byteBuffer != null) {
//                state.byteBufferFactory.release(byteBuffer);
//            }
//        }
//    }

    @Threads(Threads.MAX)
    @Benchmark
    public void benchmark(final MyState state, final Blackhole blackhole) {

        state.testMethod.accept(state, blackhole, state.byteBufferFactory);
    }


    // --------------------------------------------------------------------------------


    @State(Scope.Benchmark)
    public static class MyState {

        private static final int[] CAPACITIES = new int[]{4, 8, 16, 32, 64, 128, 512, 1_024, 2_048, 4_096, 8_192};
        private static final int CAPACITIES_LEN = CAPACITIES.length;
        private static final int RAND_COUNT = 1_000_000;
        private static final int[] RAND_CAPACITIES = new int[RAND_COUNT];
        private static final Random RANDOM = new Random(40596804673045304L);

        private final ThreadLocal<Integer> threadLocalIdx = ThreadLocal.withInitial(() -> 0);
        public ByteBufferFactory byteBufferFactory;
        public TestMethod testMethod;

        @Param({"4", "32", "1024", "8192", "random"})
//        @Param({"10"})
//        @Param({"10", "100000", "1000000000"})
        public String capacityName;

        private IntSupplier capacitySupplier;

        static {
            // fill an array with 1mil random numbers which
            for (int i = 0; i < RAND_COUNT; i++) {
                RAND_CAPACITIES[i] = CAPACITIES[RANDOM.nextInt(CAPACITIES_LEN)];
            }
        }

        private int getNextRandCapacity() {
//            final int idx = randIdx.getAndUpdate(currVal -> {
//                final int nextVal = currVal + 1;
//                return nextVal >= RAND_COUNT
//                        ? 0
//                        : nextVal;
//            });
//            return RAND_CAPACITIES[idx];
            final int currIdx = threadLocalIdx.get();
            int nextIdx = currIdx + 1;
            if (nextIdx >= RAND_COUNT) {
                nextIdx = 0;
            }
            threadLocalIdx.set(nextIdx);
            return RAND_CAPACITIES[currIdx];
        }

        public int getNextCapacity() {
            return capacitySupplier.getAsInt();
        }

        @Param({
                "ByteBufferFactoryImpl",
//                "ByteBufferPoolImpl8_asFact",
                "ByteBufferPoolImpl8_asPool",
//                "ByteBufferPoolImpl9_asFact",
                "ByteBufferPoolImpl9_asPool",
                "ByteBufferPoolImpl10_asPool",
        })
        public String impl;

        @Setup(Level.Trial)
        public void doSetup() {
            final ThreadLocalRandom random = ThreadLocalRandom.current();

            if ("random".equals(capacityName)) {
                capacitySupplier = () -> {
                    final int rndCap = getNextRandCapacity();
                    if (rndCap >= 0) {
                        return rndCap;
                    } else {
                        throw new RuntimeException("Should never happen");
                    }
                };
            } else {
                final int cap = Integer.parseInt(capacityName);
                capacitySupplier = () -> {
                    final int rndCap = getNextRandCapacity();
                    // Make sure rndCap gets used so it is similar to the random case
                    if (rndCap >= 0) {
                        return cap;
                    } else {
                        throw new RuntimeException("Should never happen");
                    }
                };
            }

            byteBufferFactory = switch (impl) {
                case "ByteBufferFactoryImpl" -> new ByteBufferFactoryImpl();
                case "ByteBufferPoolImpl8_asFact" -> new ByteBufferPoolImpl8(() -> IMPL8_CONFIG);
                case "ByteBufferPoolImpl8_asPool" -> new ByteBufferPoolImpl8(() -> IMPL8_CONFIG);
                case "ByteBufferPoolImpl9_asFact" -> new ByteBufferPoolImpl9(() -> IMPL9_CONFIG);
                case "ByteBufferPoolImpl9_asPool" -> new ByteBufferPoolImpl9(() -> IMPL9_CONFIG);
                case "ByteBufferPoolImpl10_asFact" -> new ByteBufferPoolImpl10(() -> IMPL10_CONFIG);
                case "ByteBufferPoolImpl10_asPool" -> new ByteBufferPoolImpl10(() -> IMPL10_CONFIG);
                case null, default -> throw new IllegalStateException("unknown impl: " + impl);
            };
            testMethod = switch (impl) {
                case "ByteBufferFactoryImpl",
                        "ByteBufferPoolImpl8_asFact",
                        "ByteBufferPoolImpl9_asFact",
                        "ByteBufferPoolImpl10_asFact" -> this::runAsFactory;
                case "ByteBufferPoolImpl8_asPool" -> this::runAsPool8;
                case "ByteBufferPoolImpl9_asPool" -> this::runAsPool9;
                case "ByteBufferPoolImpl10_asPool" -> this::runAsPool10;
                case null, default -> throw new IllegalStateException("unknown impl: " + impl);
            };
            LOGGER.info("Setup complete - {}", byteBufferFactory);
        }

        @TearDown(Level.Iteration)
        public void doIterationTearDown() {
            LOGGER.info("Iteration tear down complete - {}", byteBufferFactory);
        }

        @TearDown(Level.Trial)
        public void doTrialTearDown() {
            LOGGER.info("Trial tear down complete - {}", byteBufferFactory);
        }

        private void runAsFactory(MyState state, Blackhole blackhole, ByteBufferFactory factory) {
            ByteBuffer byteBuffer = null;
            try {
                final int cap = state.getNextCapacity();
                byteBuffer = state.byteBufferFactory.acquire(cap);
                checkBuffer(byteBuffer, cap);
            } finally {
                if (byteBuffer != null) {
                    state.byteBufferFactory.release(byteBuffer);
                }
            }
        }

        private void runAsPool8(MyState state, Blackhole blackhole, ByteBufferFactory factory) {
            final ByteBufferPoolImpl8 pool = (ByteBufferPoolImpl8) factory;
            final int cap = state.getNextCapacity();
            pool.doWithBuffer(cap, byteBuffer -> checkBuffer(byteBuffer, cap));
        }

        private void runAsPool9(MyState state, Blackhole blackhole, ByteBufferFactory factory) {
            final ByteBufferPoolImpl9 pool = (ByteBufferPoolImpl9) factory;
            final int cap = state.getNextCapacity();
            pool.doWithBuffer(cap, byteBuffer -> checkBuffer(byteBuffer, cap));
        }

        private void runAsPool10(MyState state, Blackhole blackhole, ByteBufferFactory factory) {
            final ByteBufferPoolImpl10 pool = (ByteBufferPoolImpl10) factory;
            final int cap = state.getNextCapacity();
            pool.doWithBuffer(cap, byteBuffer -> checkBuffer(byteBuffer, cap));
        }

        private void checkBuffer(final ByteBuffer byteBuffer, final int minCapacity) {
            if (byteBuffer.capacity() < minCapacity) {
                throw new RuntimeException(LogUtil.message("Bad capacity, wanted >= {}, got {}",
                        minCapacity, byteBuffer.capacity()));
            }
        }
    }


    // --------------------------------------------------------------------------------


    private interface TestMethod extends TriConsumer<MyState, Blackhole, ByteBufferFactory> {

    }
}
