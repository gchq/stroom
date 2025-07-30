package stroom.query.language.functions.ref;

import stroom.query.api.ErrorMessage;
import stroom.util.shared.Severity;

import java.util.List;
import java.util.function.Supplier;

public interface ErrorConsumer {

    void add(final Severity severity, final Supplier<String> message);

    void add(final Supplier<String> message);

    void add(final Throwable exception);

    List<ErrorMessage> getErrorMessages();

    List<ErrorMessage> drain();

    void clear();

    boolean hasErrors();
}
