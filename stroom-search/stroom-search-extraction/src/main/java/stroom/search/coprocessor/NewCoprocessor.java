package stroom.search.coprocessor;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.PayloadFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class NewCoprocessor implements Receiver, PayloadFactory {
    private final CoprocessorKey key;
    private final CoprocessorSettings settings;
    private final FieldIndexMap fieldIndexMap;
    private final Consumer<Error> errorConsumer;
    private final Coprocessor coprocessor;
    private final AtomicLong completionCount = new AtomicLong();

    NewCoprocessor(final CoprocessorKey key,
                   final CoprocessorSettings settings,
                   final FieldIndexMap fieldIndexMap,
                   final Consumer<Error> errorConsumer,
                   final Coprocessor coprocessor) {
        this.key = key;
        this.settings = settings;
        this.fieldIndexMap = fieldIndexMap;
        this.errorConsumer = errorConsumer;
        this.coprocessor = coprocessor;
    }

    public CoprocessorKey getKey() {
        return key;
    }

    public CoprocessorSettings getSettings() {
        return settings;
    }

    @Override
    public Consumer<Values> getValuesConsumer() {
        return values -> coprocessor.receive(values.getValues());
    }

    @Override
    public Consumer<Error> getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public Consumer<Long> getCompletionCountConsumer() {
        return completionCount::addAndGet;
    }

    @Override
    public FieldIndexMap getFieldIndexMap() {
        return fieldIndexMap;
    }

    @Override
    public Payload createPayload() {
        return coprocessor.createPayload();
    }

    public long getCompletionCount() {
        return completionCount.get();
    }
}
