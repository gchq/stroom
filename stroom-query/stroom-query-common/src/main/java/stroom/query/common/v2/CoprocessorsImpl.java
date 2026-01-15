/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

public final class CoprocessorsImpl implements Coprocessors, HasCompletionState {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CoprocessorsImpl.class);

    private final Map<Integer, Coprocessor> coprocessorMap;
    private final Map<String, TableCoprocessor> componentIdCoprocessorMap;
    private final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap;
    private final FieldIndex fieldIndex;
    private final LongAdder counter = new LongAdder();
    private final ErrorConsumer errorConsumer;
    private final ExpressionContext expressionContext;

    CoprocessorsImpl(final Map<Integer, Coprocessor> coprocessorMap,
                     final Map<String, TableCoprocessor> componentIdCoprocessorMap,
                     final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap,
                     final FieldIndex fieldIndex,
                     final ErrorConsumer errorConsumer,
                     final ExpressionContext expressionContext) {
        this.coprocessorMap = coprocessorMap;
        this.componentIdCoprocessorMap = componentIdCoprocessorMap;
        this.extractionPipelineCoprocessorMap = extractionPipelineCoprocessorMap;
        this.fieldIndex = fieldIndex;
        this.errorConsumer = errorConsumer;
        this.expressionContext = expressionContext;
    }

    @Override
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

    @Override
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
    public void accept(final Val[] values) {
        counter.increment();
        LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(values)));
        // Give the data array to each of our coprocessors
        coprocessorMap.values().forEach(coprocessor -> coprocessor.accept(values));
    }

    @Override
    public ErrorConsumer getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public ExpressionContext getExpressionContext() {
        return expressionContext;
    }

    @Override
    public CompletionState getCompletionState() {
        return new CompletionState() {
            @Override
            public void signalComplete() {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    getCompletionState(coprocessor).signalComplete();
                }
            }

            @Override
            public boolean isComplete() {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    if (!getCompletionState(coprocessor).isComplete()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void awaitCompletion() throws InterruptedException {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    getCompletionState(coprocessor).awaitCompletion();
                }
            }

            @Override
            public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
                for (final Coprocessor coprocessor : coprocessorMap.values()) {
                    if (!getCompletionState(coprocessor).awaitCompletion(timeout, unit)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    public void clear() {
        for (final Coprocessor coprocessor : coprocessorMap.values()) {
            getCompletionState(coprocessor).signalComplete();
            coprocessor.clear();
        }
    }

    private CompletionState getCompletionState(final Coprocessor coprocessor) {
        return ((HasCompletionState) coprocessor).getCompletionState();
    }

    @Override
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

    @Override
    public boolean isPresent() {
        return coprocessorMap.size() > 0;
    }

    public long getValueCount() {
        return counter.longValue();
    }

    @Override
    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    @Override
    public void forEachExtractionCoprocessor(final BiConsumer<DocRef, Set<Coprocessor>> consumer) {
        extractionPipelineCoprocessorMap.forEach(consumer);
    }

    @Override
    public long getByteSize() {
        return coprocessorMap
                .values()
                .stream()
                .map(Coprocessor::getByteSize)
                .reduce(0L, Long::sum);
    }
}
