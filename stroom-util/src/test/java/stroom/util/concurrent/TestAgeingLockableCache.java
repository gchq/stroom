package stroom.util.concurrent;

import org.junit.Test;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestAgeingLockableCache {

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

        private final ConcurrentLinkedDeque<T> values;
        private final Supplier<T> supplier;

        private MultithreadedCaller(final Supplier<T> supplier) {
            this.values = new ConcurrentLinkedDeque<>();
            this.supplier = supplier;
        }

        @Override
        public void run() {
            values.add(supplier.get());
        }

        public long matchingValue(final T value) {
            return values.stream().filter(value::equals).count();
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

    private static final Long ONE_SECOND = 1000L;
    private static final Long MAXIMUM_AGE = 10 * ONE_SECOND;

    @Test
    public void testForceRebuild() {

        final SlowSupplier<String> valueSupplier = new SlowSupplier<>(ONE_SECOND);
        final TimeSupplier timeSupplier = new TimeSupplier();
        final AgeingLockableCache<String> lockable = AgeingLockableCache.<String>protect()
                .maximumAge(MAXIMUM_AGE)
                .timeSupplier(timeSupplier)
                .fetch(valueSupplier)
                .build();

        final String firstValue = "First";
        final String secondValue = "Second";

        // Give the supplier a value to serve up
        valueSupplier.setCurrentValue(firstValue);

        // Try and get that value
        final String result0 = lockable.get();
        assertEquals(firstValue, result0);

        // Call get a bunch more times, multiple threads at once
        thrashWithThreads(lockable, firstValue);

        // These subsequent calls should not result in additional calls to the underlying supplier
        assertEquals(1, valueSupplier.getNumberRequestsForValue());

        // Change the value of the underlying supplier
        valueSupplier.setCurrentValue(secondValue);

        // Get the value again, it should still be the first one
        final String result1 = lockable.get();
        assertEquals(firstValue, result1);

        // Since the ageing lockable has not been told to rebuild, the underlying supplier should be left untouched
        assertEquals(0, valueSupplier.getNumberRequestsForValue());

        // Now trigger the rebuild on the next fetch
        lockable.forceRebuild();

        // Call get a bunch more times, multiple threads at once
        thrashWithThreads(lockable, secondValue);

        // Now that a rebuild has been forced, the next get should return the updated value
        // All of those accesses should have resulted in a single call to underlying supplier
        // All other calls should have waited for the first
        assertEquals(1, valueSupplier.getNumberRequestsForValue());
    }

    @Test
    public void testAge() {
        final SlowSupplier<String> valueSupplier = new SlowSupplier<>(ONE_SECOND);
        final TimeSupplier timeSupplier = new TimeSupplier();
        final AgeingLockableCache<String> lockable = AgeingLockableCache.<String>protect()
                .maximumAge(MAXIMUM_AGE)
                .timeSupplier(timeSupplier)
                .fetch(valueSupplier)
                .build();

        final String firstValue = "First";
        final String secondValue = "Second";

        // Give the supplier a value to serve up
        valueSupplier.setCurrentValue(firstValue);

        // Call get() a bunch of times, expecting the first value to be served up in all cases.
        thrashWithThreads(lockable, firstValue);

        // These subsequent calls should not result in additional calls to the underlying supplier
        assertEquals(1, valueSupplier.getNumberRequestsForValue());

        // Change the value of the underlying supplier
        valueSupplier.setCurrentValue(secondValue);

        // Get the value again, it should still be the first one
        final String result1 = lockable.get();
        assertEquals(firstValue, result1);

        // Since the ageing lockable has not been told to rebuild, the underlying supplier should be left untouched
        assertEquals(0, valueSupplier.getNumberRequestsForValue());

        // Force the time forwards on the cache, so that the next call decides that the cache must be refreshed
        timeSupplier.jumpTime(MAXIMUM_AGE * 2);

        // Call get a bunch more times, multiple threads at once, now expecting the second value to be served up
        thrashWithThreads(lockable, secondValue);

        // Now that a rebuild has been forced, the next get should return the updated value
        // All of those accesses should have resulted in a single call to underlying supplier
        // All other calls should have waited for the first
        assertEquals(1, valueSupplier.getNumberRequestsForValue());
    }


    /**
     * Given a lockable cache, it spins up a pool of threads to repeatedly hammer it.
     * It should find that the underlying supplier is not called any more times,
     *
     * @param lockable The lockable cache under test
     * @param expectedValue The value being supplied through this test
     * @param <T> The type that the cache is tied to
     */
    private <T> void thrashWithThreads(final AgeingLockableCache<T> lockable,
                                       final T expectedValue) {

        // Call get a bunch more times, multiple threads at once
        final long successiveCalls = 5;
        final MultithreadedCaller<T> caller0 = new MultithreadedCaller<>(lockable::get);
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
