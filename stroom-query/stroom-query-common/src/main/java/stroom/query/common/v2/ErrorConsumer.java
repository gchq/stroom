package stroom.query.common.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ErrorConsumer implements Consumer<Throwable> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorConsumer.class);

    private final List<String> errors = new ArrayList<>();

    @Override
    public synchronized void accept(final Throwable exception) {
        LOGGER.debug(exception.getMessage(), exception);
        if (errors.size() < 1000) {
            errors.add(exception.getMessage());
        }
    }

    public synchronized List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public synchronized List<String> drain() {
        final List<String> copy = new ArrayList<>(errors);
        errors.clear();
        return copy;
    }
}
