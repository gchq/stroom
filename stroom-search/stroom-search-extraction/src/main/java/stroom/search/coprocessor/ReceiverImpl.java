package stroom.search.coprocessor;

import stroom.dashboard.expression.v1.FieldIndexMap;

import java.util.function.Consumer;

public class ReceiverImpl implements Receiver {
    private final Consumer<Values> valuesConsumer;
    private final Consumer<Error> errorConsumer;
    private final Consumer<Long> completionCountConsumer;
    private final FieldIndexMap fieldIndexMap;

    public ReceiverImpl(final Consumer<Values> valuesConsumer,
                        final Consumer<Error> errorConsumer,
                        final Consumer<Long> completionCountConsumer,
                        final FieldIndexMap fieldIndexMap) {
        this.valuesConsumer = valuesConsumer;
        this.errorConsumer = errorConsumer;
        this.completionCountConsumer = completionCountConsumer;
        this.fieldIndexMap = fieldIndexMap;
    }

    @Override
    public Consumer<Values> getValuesConsumer() {
        return valuesConsumer;
    }

    @Override
    public Consumer<Error> getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public Consumer<Long> getCompletionCountConsumer() {
        return completionCountConsumer;
    }

    @Override
    public FieldIndexMap getFieldIndexMap() {
        return fieldIndexMap;
    }
}