package stroom.query.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LambdaLoggerFactory {
    private LambdaLoggerFactory() {
        // Factory.
    }

    public static final LambdaLogger getLogger(final Class<?> clazz) {
        final Logger logger = LoggerFactory.getLogger(clazz.getName());

        return new BasicLambdaLogger(logger);
    }
}
