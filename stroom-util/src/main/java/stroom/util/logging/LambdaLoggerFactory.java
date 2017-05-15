package stroom.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

public final class LambdaLoggerFactory {
    private LambdaLoggerFactory() {
        // Factory.
    }

    public static final LambdaLogger getLogger(final Class<?> clazz) {
        final Logger logger = LoggerFactory.getLogger(clazz.getName());

        if (logger instanceof LocationAwareLogger) {
            return new LocationAwareLambdaLogger((LocationAwareLogger) logger);
        }

        return new BasicLambdaLogger(logger);
    }
}
