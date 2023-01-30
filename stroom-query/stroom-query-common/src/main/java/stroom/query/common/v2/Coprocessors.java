package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import javax.validation.constraints.NotNull;

public final class Coprocessors implements Iterable<Coprocessor>, ValuesConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Coprocessors.class);

    private final Map<Integer, Coprocessor> coprocessorMap;
    private final Map<String, TableCoprocessor> componentIdCoprocessorMap;
    private final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap;
    private final FieldIndex fieldIndex;
    private final LongAdder counter = new LongAdder();
    private final ErrorConsumer errorConsumer;

    Coprocessors(final Map<Integer, Coprocessor> coprocessorMap,
                 final Map<String, TableCoprocessor> componentIdCoprocessorMap,
                 final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap,
                 final FieldIndex fieldIndex,
                 final ErrorConsumer errorConsumer) {
        this.coprocessorMap = coprocessorMap;
        this.componentIdCoprocessorMap = componentIdCoprocessorMap;
        this.extractionPipelineCoprocessorMap = extractionPipelineCoprocessorMap;
        this.fieldIndex = fieldIndex;
        this.errorConsumer = errorConsumer;
    }

    public void readPayloads(final Input input) {
        // If the remote node hasn't started yet it will return 0 results so by default we need to tell the calling
        // process that we still want to keep polling by returning true by default.
        final int length = input.readInt();
        for (int i = 0; i < length; i++) {
            final int coprocessorId = input.readInt();
            final Coprocessor coprocessor = coprocessorMap.get(coprocessorId);
            coprocessor.readPayload(input);
        }
    }

    public void writePayloads(final Output output) {
        output.writeInt(coprocessorMap.size());
        for (final Entry<Integer, Coprocessor> entry : coprocessorMap.entrySet()) {
            final int coprocessorId = entry.getKey();
            final Coprocessor coprocessor = entry.getValue();
            output.writeInt(coprocessorId);
            coprocessor.writePayload(output);
        }
    }

    @Override
    public void add(final Val[] values) {
        counter.increment();
        LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(values)));
        // Give the data array to each of our coprocessors
        coprocessorMap.values().forEach(coprocessor -> coprocessor.add(values));
    }

    public ErrorConsumer getErrorConsumer() {
        return errorConsumer;
    }

    public CompletionState getCompletionState() {
        return new CompletionState() {
            @Override
            public void signalComplete() {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    coprocessor.getCompletionState().signalComplete();
                }
            }

            @Override
            public boolean isComplete() {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    if (!coprocessor.getCompletionState().isComplete()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void awaitCompletion() throws InterruptedException {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    coprocessor.getCompletionState().awaitCompletion();
                }
            }

            @Override
            public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    if (!coprocessor.getCompletionState().awaitCompletion(timeout, unit)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    public void clear() {
        for (final Coprocessor coprocessor : coprocessorMap.values()) {
            coprocessor.getCompletionState().signalComplete();
            coprocessor.clear();
        }
    }

    public Coprocessor get(final int coprocessorId) {
        return coprocessorMap.get(coprocessorId);
    }

    public DataStore getData(final String componentId) {
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
    @NotNull
    public Iterator<Coprocessor> iterator() {
        return coprocessorMap.values().iterator();
    }

    public long getValueCount() {
        return counter.longValue();
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    public void forEachExtractionCoprocessor(final BiConsumer<DocRef, Set<Coprocessor>> consumer) {
        extractionPipelineCoprocessorMap.forEach(consumer);
    }

    public long getByteSize() {
        return coprocessorMap
                .values()
                .stream()
                .map(Coprocessor::getByteSize)
                .reduce(0L, Long::sum);
    }
}
