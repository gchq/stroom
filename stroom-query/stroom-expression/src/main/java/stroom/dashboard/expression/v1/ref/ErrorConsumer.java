package stroom.dashboard.expression.v1.ref;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface ErrorConsumer {

    void add(final Supplier<String> message);

    void add(final Throwable exception);

    List<String> getErrors();

    default Stream<String> stream() {
        final List<String> errors = getErrors();
        if (errors == null || errors.size() == 0) {
            return Stream.empty();
        } else {
            return errors.stream();
        }
    }

    List<String> drain();

    boolean hasErrors();
}
