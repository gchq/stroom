package stroom.util.logging;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.SimpleMetrics.LocalMetrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestSimpleMetrics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSimpleMetrics.class);

    @Test
    void testLocalMetrics_Disabled() {
        final LocalMetrics localMetrics = SimpleMetrics.createLocalMetrics(false);
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        localMetrics.measure("x", () -> wasCalled.set(true));
        assertThat(wasCalled)
                .isTrue();

        final Boolean result = localMetrics.measure("y", () -> {
            wasCalled.set(true);
            return wasCalled.get();
        });

        assertThat(wasCalled)
                .isTrue();
        assertThat(result)
                .isTrue();

        localMetrics.toString();

        localMetrics.reset();
    }

    @Test
    void testLocalMetrics_Enabled() {
        final LocalMetrics localMetrics = SimpleMetrics.createLocalMetrics(LOGGER.isInfoEnabled());
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        localMetrics.measure("x", () -> wasCalled.set(true));
        assertThat(wasCalled)
                .isTrue();
        localMetrics.measure("x", () -> {
            ThreadUtil.sleep(1_000);
            wasCalled.set(true);
        });
        localMetrics.measure("x", () -> wasCalled.set(true));

        final Boolean result = localMetrics.measure("y", () -> {
            wasCalled.set(true);
            return wasCalled.get();
        });

        assertThat(wasCalled)
                .isTrue();
        assertThat(result)
                .isTrue();

        final String output = localMetrics.toString();
        LOGGER.info("toString:\n{}", output);

        localMetrics.reset();
    }
}
