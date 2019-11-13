package stroom.search.coprocessor;

import stroom.dashboard.expression.v1.FieldIndexMap;

import java.util.function.Consumer;

public interface Receiver {
    FieldIndexMap getFieldIndexMap();

    Consumer<Values> getValuesConsumer();

    Consumer<Error> getErrorConsumer();

    Consumer<Long> getCompletionCountConsumer();
}
