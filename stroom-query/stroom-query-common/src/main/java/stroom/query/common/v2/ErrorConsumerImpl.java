package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ErrorConsumerImpl implements ErrorConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorConsumerImpl.class);

    private static final int MAX_ERROR_COUNT = 100;

    private final Set<String> errors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger errorCount = new AtomicInteger();

    @Override
    public void add(final Throwable exception) {
        LOGGER.debug(exception::getMessage, exception);

        final String message;
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            message = exception.getClass().getName();
        } else {
            message = exception.getMessage();
        }

        int count = errorCount.incrementAndGet();
        if (count <= MAX_ERROR_COUNT) {
            errors.add(message);
        }
    }

    @Override
    public List<String> getErrors() {
        if (hasErrors()) {
            return new ArrayList<>(errors);
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> drain() {
        if (hasErrors()) {
            final List<String> copy = new ArrayList<>(errors);
            copy.forEach(errors::remove);
            return copy;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasErrors() {
        return errorCount.get() > 0;
    }

    @Override
    public String toString() {
        if (hasErrors()) {
            return String.join("\n", errors);
        }
        return null;
    }
}
