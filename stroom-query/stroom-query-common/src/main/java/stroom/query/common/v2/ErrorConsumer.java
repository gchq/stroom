package stroom.query.common.v2;

import stroom.util.NullSafe;

import java.util.List;
import java.util.stream.Stream;

public interface ErrorConsumer {

    void add(final Throwable exception);

    List<String> getErrors();

    default Stream<String> stream() {
        final List<String> errors = getErrors();
        if (NullSafe.isEmptyCollection(errors)) {
            return Stream.empty();
        } else {
            return errors.stream();
        }
    }

    List<String> drain();

    boolean hasErrors();
}
