package stroom.search.coprocessor;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.PayloadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class NewCoprocessor implements Receiver, PayloadFactory {
    private final CountDownLatch completionState = new CountDownLatch(1);
    private final CoprocessorKey key;
    private final CoprocessorSettings settings;
    private final Consumer<Error> errorConsumer;
    private final Coprocessor coprocessor;
    private final FieldIndexMap fieldIndexMap;
    private final AtomicLong valuesCount = new AtomicLong();
    private final AtomicLong completionCount = new AtomicLong();

    NewCoprocessor(final CoprocessorKey key,
                   final CoprocessorSettings settings,
                   final Consumer<Error> errorConsumer,
                   final Coprocessor coprocessor,
                   final FieldIndexMap fieldIndexMap) {
        this.key = key;
        this.settings = settings;
        this.errorConsumer = errorConsumer;
        this.coprocessor = coprocessor;
        this.fieldIndexMap = fieldIndexMap;
    }

    public CoprocessorKey getKey() {
        return key;
    }

    public CoprocessorSettings getSettings() {
        return settings;
    }

    @Override
    public Consumer<Values> getValuesConsumer() {
        return values -> {
            valuesCount.incrementAndGet();
            coprocessor.receive(values.getValues());
        };
    }

    @Override
    public Consumer<Error> getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public Consumer<Long> getCompletionConsumer() {
        return count -> {
            completionCount.set(count);
            completionState.countDown();
        };
    }

    @Override
    public Payload createPayload() {
        return coprocessor.createPayload();
    }

    public AtomicLong getValuesCount() {
        return valuesCount;
    }

    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completionState.await(timeout, unit);
    }

    public FieldIndexMap getFieldIndexMap() {
        return fieldIndexMap;
    }
}
