package stroom.util.concurrent;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestModelCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestModelCache.class);

    private static final Long ONE_SECOND = 1000L;
    private static final Long MAXIMUM_AGE = 10 * ONE_SECOND;

    @Test
    public void testForceRebuild() {

        final SlowSupplier<String> valueSupplier = new SlowSupplier<>(ONE_SECOND);
        final TimeSupplier timeSupplier = new TimeSupplier();
        final ModelCache<String> modelCache = new ModelCache.Builder<String>()
                .maxAge(MAXIMUM_AGE)
                .timeSupplier(timeSupplier)
                .valueSupplier(valueSupplier)
                .build();

        testRefreshRequired(valueSupplier, timeSupplier, modelCache, modelCache::rebuild);
    }

    @Test
    public void testAge() {
        final SlowSupplier<String> valueSupplier = new SlowSupplier<>(ONE_SECOND);
        final TimeSupplier timeSupplier = new TimeSupplier();
        final ModelCache<String> modelCache = new ModelCache.Builder<String>()
                .maxAge(MAXIMUM_AGE)
                .timeSupplier(timeSupplier)
                .valueSupplier(valueSupplier)
                .build();

        testRefreshRequired(valueSupplier, timeSupplier, modelCache, () -> timeSupplier.jumpTime(MAXIMUM_AGE * 2));
    }

    @Test
    public void testCombined() {
        final SlowSupplier<String> valueSupplier = new SlowSupplier<>(ONE_SECOND);
        final TimeSupplier timeSupplier = new TimeSupplier();
        final ModelCache<String> modelCache = new ModelCache.Builder<String>()
                .maxAge(MAXIMUM_AGE)
                .timeSupplier(timeSupplier)
                .valueSupplier(valueSupplier)
                .build();

        testRefreshRequired(valueSupplier, timeSupplier, modelCache, modelCache::rebuild);
        modelCache.rebuild();
        testRefreshRequired(valueSupplier, timeSupplier, modelCache, () -> timeSupplier.jumpTime(MAXIMUM_AGE * 2));
        timeSupplier.jumpTime(MAXIMUM_AGE * 2);
        testRefreshRequired(valueSupplier, timeSupplier, modelCache, modelCache::rebuild);
    }

    /**
     * Used as the supplier of values, with the supply function taking time defined by sleep time
     * @param <T>
     */
    private class SlowSupplier<T> implements Supplier<T> {

        private T currentValue;

        private long numberRequestsForValue;

        private final long sleepTime;

        SlowSupplier(long sleepTime) {
            this.sleepTime = sleepTime;
        }

        @Override
        public T get() {
            try {
                numberRequestsForValue++;
                Thread.sleep(sleepTime);
            } catch (final InterruptedException e) {
                fail(String.format("Could not sleep for required time %s", e));
            }

            return currentValue;
        }

        long getNumberRequestsForValue() {
            return numberRequestsForValue;
        }

        void setCurrentValue(final T value) {
            this.currentValue = value;
            this.numberRequestsForValue = 0;
        }
    }

    /**
     * This runnable will make a call to the underlying supplier and put the value into a thread safe queue.
     * The expectation is that an instance of this will be given to several threads to thrash a supplier.
     * The values extracted will be put into a queue for later assertions.
     *
     * @param <T> The type of object the supplier yields
     */
    private class MultithreadedCaller<T> implements Runnable {

        private class TimedValue<T> {
            private final T value;
            private final Long timestamp;
            
            TimedValue(final T value, final Long timestamp) 
            {
                this.value = value;
                this.timestamp = timestamp;
            }

            public T getValue() {
                return value;
            }
        }
        
        private final ConcurrentLinkedDeque<TimedValue<T>> values;
        private final Supplier<T> valueSupplier;
        private final Supplier<Long> timeSupplier;

        private MultithreadedCaller(final Supplier<T> valueSupplier,
                                    final Supplier<Long> timeSupplier) {
            this.values = new ConcurrentLinkedDeque<>();
            this.valueSupplier = valueSupplier;
            this.timeSupplier = timeSupplier;
        }

        @Override
        public void run() {
            final T value = valueSupplier.get();
            final Long time = timeSupplier.get();
            values.add(new TimedValue<>(value, time));
        }

        long matchingValue(final T value) {
            LOGGER.info(String.format("Checking for Value %s", value));
            
            return values.stream()
                    .peek(d -> LOGGER.info(String.format("\t%s at %d", d.value.toString(), d.timestamp)))
                    .map(TimedValue::getValue)
                    .filter(value::equals)
                    .count();
        }
    }

    /**
     * Used to fiddle with the clock being used by the class under test.
     * Allows a test to throw time forward without having to actually 'wait'.
     * This works if the class under test allows plugging of alternative suppliers of time.
     */
    private class TimeSupplier implements Supplier<Long> {
        private long timeToJump = 0L;

        void jumpTime(final long jump) {
            this.timeToJump += jump;
        }

        public Long get() {
            return System.currentTimeMillis() + timeToJump;
        }
    }

    /**
     * General form of a test that moves the value supplier between two values, with some event to trigger the rebuild.
     * 
     * @param valueSupplier The underlying supplier of values
     * @param timeSupplier  The time supplier
     * @param modelCache      The model cache object under test
     * @param causeRebuild  A function that should cause a rebuild
     */
    private void testRefreshRequired(final SlowSupplier<String> valueSupplier,
                                     final TimeSupplier timeSupplier,
                                     final ModelCache<String> modelCache,
                                     final Runnable causeRebuild) {

        final String firstValue = UUID.randomUUID().toString();
        final String secondValue = UUID.randomUUID().toString();

        LOGGER.info(String.format("Testing Refresh with First: %s, Second: %s", firstValue, secondValue));

        // Give the supplier a value to serve up
        valueSupplier.setCurrentValue(firstValue);

        // Try and get that value
        final String result0 = modelCache.get();
        assertEquals(firstValue, result0);

        // Call get a bunch more times, multiple threads at once
        thrashWithThreads(modelCache::get, timeSupplier, firstValue);

        // These subsequent calls should not result in additional calls to the underlying supplier
        assertEquals(1, valueSupplier.getNumberRequestsForValue());

        // Change the value of the underlying supplier
        valueSupplier.setCurrentValue(secondValue);

        // Get the value again, it should still be the first one
        final String result1 = modelCache.get();
        assertEquals(firstValue, result1);

        // Since the ageing modelCache has not been told to rebuild, the underlying supplier should be left untouched
        assertEquals(0, valueSupplier.getNumberRequestsForValue());

        // Now trigger the rebuild on the next fetch
        causeRebuild.run();

        // Call get a bunch more times, multiple threads at once
        thrashWithThreads(modelCache::get, timeSupplier, secondValue);

        // Now that a rebuild has been forced, the next get should return the updated value
        // All of those accesses should have resulted in a single call to underlying supplier
        // All other calls should have waited for the first
        assertEquals(1, valueSupplier.getNumberRequestsForValue());
    }

    /**
     * Given a modelCache cache, it spins up a pool of threads to repeatedly hammer it.
     * It should find that the underlying supplier is not called any more times,
     *
     * @param valueSupplier The modelCache cache under test
     * @param expectedValue The value being supplied through this test
     * @param <T> The type that the cache is tied to
     */
    private <T> void thrashWithThreads(final Supplier<T> valueSupplier,
                                       final Supplier<Long> timeSupplier,
                                       final T expectedValue) {

        // Call get a bunch more times, multiple threads at once
        final long successiveCalls = 5;
        final MultithreadedCaller<T> caller0 = new MultithreadedCaller<>(valueSupplier, timeSupplier);
        final ExecutorService exec = Executors.newFixedThreadPool((int) successiveCalls);

        LongStream.range(0, successiveCalls)
                .forEach(i -> exec.submit(caller0));

        try {
            exec.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail(e.getLocalizedMessage());
        }

        final long matchingFirst = caller0.matchingValue(expectedValue);
        assertEquals(successiveCalls, matchingFirst);
    }
}
