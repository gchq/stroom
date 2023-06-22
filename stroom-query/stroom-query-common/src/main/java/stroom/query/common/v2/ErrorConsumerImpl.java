package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ErrorConsumerImpl implements ErrorConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorConsumerImpl.class);

    private static final int MAX_ERROR_COUNT = 100;

    private final Set<Throwable> errors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger errorCount = new AtomicInteger();

    public ErrorConsumerImpl() {
        LOGGER.debug("Creating errorConsumer {}", this);
    }

    @Override
    public void add(final Supplier<String> message) {
        int count = errorCount.incrementAndGet();
        if (count <= MAX_ERROR_COUNT) {
            final String string = message.get();
            final RuntimeException exception = new RuntimeException(string);
            LOGGER.trace(exception::getMessage, exception);
            errors.add(exception);
        }
    }

    @Override
    public void add(final Throwable exception) {
        LOGGER.trace(exception::getMessage, exception);
        if (!ErrorConsumerUtil.isInterruption(exception)) {
            int count = errorCount.incrementAndGet();
            if (count <= MAX_ERROR_COUNT) {
                errors.add(exception);
            }
        }
    }

    @Override
    public List<String> getErrors() {
        if (hasErrors()) {
            return errors
                    .stream()
                    .map(ExceptionStringUtil::getMessage)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> drain() {
        if (hasErrors()) {
            final Set<Throwable> copy = new HashSet<>(errors);
            copy.forEach(errors::remove);
            return copy
                    .stream()
                    .map(ExceptionStringUtil::getMessage)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean hasErrors() {
        return errorCount.get() > 0;
    }

    @Override
    public String toString() {
        return "id=" + System.identityHashCode(this)
                + " errorCount=" + errorCount.get();
    }
}
