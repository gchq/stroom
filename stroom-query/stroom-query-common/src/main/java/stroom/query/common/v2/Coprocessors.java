package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public class Coprocessors implements Iterable<Coprocessor> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Coprocessors.class);

    private final Map<CoprocessorKey, Coprocessor> coprocessorMap;
    private final Map<String, TableCoprocessor> componentIdCoprocessorMap;
    private final FieldIndex fieldIndex;
    private final LongAdder counter = new LongAdder();
    private final ErrorConsumer errorConsumer;

    Coprocessors(final Map<CoprocessorKey, Coprocessor> coprocessorMap,
                 final Map<String, TableCoprocessor> componentIdCoprocessorMap,
                 final FieldIndex fieldIndex,
                 final ErrorConsumer errorConsumer) {
        this.coprocessorMap = coprocessorMap;
        this.componentIdCoprocessorMap = componentIdCoprocessorMap;
        this.fieldIndex = fieldIndex;
        this.errorConsumer = errorConsumer;
    }

    public List<Payload> createPayloads() {
        // Produce payloads for each coprocessor.
        List<Payload> payloads = null;
        for (final Coprocessor coprocessor : coprocessorMap.values()) {
            final Payload payload = coprocessor.createPayload();
            if (payload != null) {
                if (payloads == null) {
                    payloads = new ArrayList<>();
                }

                payloads.add(payload);
            }
        }
        return payloads;
    }

    public boolean consumePayloads(final List<Payload> payloads) {
        boolean partialSuccess = true;
        if (payloads != null && payloads.size() > 0) {
            partialSuccess = false;
            for (final Payload payload : payloads) {
                final boolean success = get(payload.getKey()).consumePayload(payload);
                if (success) {
                    partialSuccess = true;
                }
            }
        }
        return partialSuccess;
    }

    public Consumer<Val[]> getValuesConsumer() {
        return values -> {
            counter.increment();
            LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(values)));

            // Give the data array to each of our coprocessors
            coprocessorMap.values().forEach(coprocessor -> coprocessor.getValuesConsumer().accept(values));
        };
    }

    public ErrorConsumer getErrorConsumer() {
        return errorConsumer;
    }

    public Coprocessor get(final CoprocessorKey key) {
        return coprocessorMap.get(key);
    }

    public Data getData(final String componentId) {
        LOGGER.debug(() -> LogUtil.message("getData called for componentId {}", componentId));
        final TableCoprocessor tableCoprocessor = componentIdCoprocessorMap.get(componentId);
        if (tableCoprocessor != null) {
            return tableCoprocessor.getData();
        }
        return null;
    }

    public int size() {
        return coprocessorMap.size();
    }

    @Override
    public Iterator<Coprocessor> iterator() {
        return coprocessorMap.values().iterator();
    }

    public long getValueCount() {
        return counter.longValue();
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }
}
