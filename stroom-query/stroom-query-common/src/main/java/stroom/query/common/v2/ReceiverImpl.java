package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import java.util.function.Consumer;

public class ReceiverImpl implements Receiver {
    private final Consumer<Val[]> valuesConsumer;
    private final Consumer<Throwable> errorConsumer;
    private final Consumer<Long> completionConsumer;

    public ReceiverImpl(final Consumer<Val[]> valuesConsumer,
                        final Consumer<Throwable> errorConsumer,
                        final Consumer<Long> completionConsumer) {
        this.valuesConsumer = valuesConsumer;
        this.errorConsumer = errorConsumer;
        this.completionConsumer = completionConsumer;
    }

    @Override
    public Consumer<Val[]> getValuesConsumer() {
        return valuesConsumer;
    }

    @Override
    public Consumer<Throwable> getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public Consumer<Long> getCompletionConsumer() {
        return completionConsumer;
    }
}