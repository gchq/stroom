package stroom.query.common.v2;

import java.util.List;

public interface ErrorConsumer {

    void add(final Throwable exception);

    List<String> getErrors();

    List<String> drain();

    boolean hasErrors();
}
