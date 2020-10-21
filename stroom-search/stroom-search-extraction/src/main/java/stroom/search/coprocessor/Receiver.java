package stroom.search.coprocessor;

import java.util.function.Consumer;

public interface Receiver {
    Consumer<Values> getValuesConsumer();

    Consumer<Error> getErrorConsumer();

    Consumer<Long> getCompletionConsumer();
}
