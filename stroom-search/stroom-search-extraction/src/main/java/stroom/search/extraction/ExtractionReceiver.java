package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;

import java.util.function.Consumer;

public class ExtractionReceiver extends ReceiverImpl {
    private final FieldIndexMap fieldIndexMap;

    public ExtractionReceiver(final Consumer<Values> valuesConsumer,
                              final Consumer<Error> errorConsumer,
                              final Consumer<Long> completionConsumer,
                              final FieldIndexMap fieldIndexMap) {
        super(valuesConsumer, errorConsumer, completionConsumer);
        this.fieldIndexMap = fieldIndexMap;
    }

    public FieldIndexMap getFieldIndexMap() {
        return fieldIndexMap;
    }
}
