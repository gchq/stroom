package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ErrorConsumerImpl implements ErrorConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorConsumerImpl.class);

    private final ArrayBlockingQueue<String> errors = new ArrayBlockingQueue<>(1000);

    @Override
    public void add(final Throwable exception) {
        LOGGER.debug(exception::getMessage, exception);
        if (errors.size() < 1000) {
            errors.offer(exception.getMessage());
        }
    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    @Override
    public List<String> drain() {
        final List<String> copy = new ArrayList<>(errors.size());
        errors.drainTo(copy);
        return copy;
    }
}
