package stroom.dashboard.expression.v1;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;


class TestFunctionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFunctionFactory.class);

    @Test
    void test() {
        final Instant start = Instant.now();

        final FunctionFactory functionFactory = new FunctionFactory();

        LOGGER.info("Completed in {}", Duration.between(start, Instant.now()));
    }
}