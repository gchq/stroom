package stroom.util.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TestLambdaLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLambdaLogger.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestLambdaLogger.class);

    @Test
    void compareLambdaToIfBlock() {

        String someStr = "sdskjdsdsdk";

        Stream.of(100, 1_000, 1_000_000, 100_000_000)
                .forEach(iterations -> {

            LOGGER.info("Iterations: {}", iterations);

            final Instant lambdaLoggerStartTime = Instant.now();
            for (int i = 0; i < iterations; i++) {
                // Use a complex msg
                LAMBDA_LOGGER.trace(() -> LogUtil.message("This is my msg {} {}",
                        iterations, lambdaLoggerStartTime.atOffset(ZoneOffset.MIN)));

                LAMBDA_LOGGER.trace(() -> LogUtil.message("This is another msg {} {} {}",
                        someStr, iterations, lambdaLoggerStartTime.atOffset(ZoneOffset.MIN)));

                LAMBDA_LOGGER.trace(() -> LogUtil.message("This is yet another msg {}",
                        iterations));
            }
            final Duration lambdaLoggerDuration = Duration.between(lambdaLoggerStartTime, Instant.now());

            LOGGER.info("Duration for LAMBDA_LOGGER trace(() -> LogUtil.message(...)) {} ({}ms)",
                    lambdaLoggerDuration, lambdaLoggerDuration.toMillis());

            final Instant loggerStartTime = Instant.now();

            for (int i = 0; i < iterations; i++) {
                // Use a complex msg
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(LogUtil.message("This is my msg {} {}",
                            iterations, loggerStartTime.atOffset(ZoneOffset.MIN)));
                }
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(LogUtil.message("This is another msg {} {} {}",
                            someStr, iterations, loggerStartTime.atOffset(ZoneOffset.MIN)));
                }
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(LogUtil.message("This is yet another my msg {}",
                            iterations));
                }
            }
            final Duration loggerDuration = Duration.between(loggerStartTime, Instant.now());

            LOGGER.info("Duration for LOGGER if (LOGGER.isTraceEnabled()) {} ({}ms)",
                    loggerDuration, loggerDuration.toMillis());

            LOGGER.info("ms diff {}", lambdaLoggerDuration.toMillis() - loggerDuration.toMillis());
            LOGGER.info("%  diff {}",
                    (lambdaLoggerDuration.toMillis() - loggerDuration.toMillis())
                            / (double) loggerDuration.toMillis() * 100);
            
            LOGGER.info("---------------------------");
        });
    }
}