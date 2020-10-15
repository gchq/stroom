package stroom.search.coprocessor;

import java.util.function.Consumer;

public class ReceiverImpl implements Receiver {
    private final Consumer<Values> valuesConsumer;
    private final Consumer<Error> errorConsumer;
    private final Consumer<Long> completionConsumer;

    public ReceiverImpl(final Consumer<Values> valuesConsumer,
                        final Consumer<Error> errorConsumer,
                        final Consumer<Long> completionConsumer) {
        this.valuesConsumer = valuesConsumer;
        this.errorConsumer = errorConsumer;
        this.completionConsumer = completionConsumer;
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
    public Consumer<Long> getCompletionConsumer() {
        return completionConsumer;
    }
}