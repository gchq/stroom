package stroom.util.concurrent;

import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class TestAsyncReference extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAsyncReference.class);

    @Test
    void test() {
        final ExecutorService executor = Executors.newCachedThreadPool();
        final AsyncReference<Integer> asyncReference = new AsyncReference<>(value -> {
            ThreadUtil.sleep(100);
            if (value == null) {
                return 1;
            }
            return value + 1;
        }, Duration.ofMillis(100), executor);

        // Run for 10 seconds.
        final Instant start = Instant.now();
        int updates = 0;
        int calls = 0;
        while (Instant.now().isBefore(start.plus(Duration.ofSeconds(1)))) {
            updates = asyncReference.get();
            calls++;
        }

        LOGGER.info("Updates = " + updates);
        LOGGER.info("Calls = " + calls);

        assertThat(updates).isGreaterThan(2);
        assertThat(calls).isGreaterThan(1000);
    }
}
