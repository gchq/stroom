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

/*
 * Taken from https://github.com/rovats/java-utils
 *
 * Copyright (C) 2016, 2017, 2018, 2019, 2020 Rohit Vats
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
 * See the License for the specific
 *
 */

package stroom.util.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Collects elements in the stream and calls the supplied batch processor
 * after the configured batch size is reached.
 * <p>
 * In case of a parallel stream, the batch processor may be called with
 * elements less than the batch size.
 * <p>
 * The elements are not kept in memory, and the final result will be an
 * empty list.
 *
 * @param <T> Type of the elements being collected
 */
public class BatchingCollector<T> implements Collector<T, List<T>, List<T>> {

    private final int batchSize;
    private final Consumer<List<T>> batchProcessor;
    private final AtomicLong recordsProcessed = new AtomicLong(0);


    /**
     * Constructs the batch collector
     *
     * @param batchSize      the batch size after which the batchProcessor should be called
     * @param batchProcessor the batch processor which accepts batches of records to process
     */
    BatchingCollector(final int batchSize, final Consumer<List<T>> batchProcessor) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        this.batchSize = batchSize;
        this.batchProcessor = Objects.requireNonNull(batchProcessor);
    }

    /**
     * Creates a new batch collector
     *
     * @param batchSize      the batch size after which the batchProcessor should be called
     * @param batchProcessor the batch processor which accepts batches of records to process
     * @param <T>            the type of elements being processed
     * @return a batch collector instance
     */
    public static <T> BatchingCollector<T> of(final int batchSize, final Consumer<List<T>> batchProcessor) {
        return new BatchingCollector<T>(batchSize, batchProcessor);
    }

    public Supplier<List<T>> supplier() {
        return ArrayList::new;
    }

    public BiConsumer<List<T>, T> accumulator() {
        return (ts, t) -> {
            ts.add(t);
            final List<T> batch = new ArrayList<>(ts);
            if (ts.size() >= batchSize) {
                batchProcessor.accept(batch);
                recordsProcessed.addAndGet(batch.size());
                ts.clear();
            }
        };
    }

    public BinaryOperator<List<T>> combiner() {
        return (left, right) -> {
            // process each parallel list without checking for batch size
            // avoids adding all elements of one to another
            // can be modified if a strict batching mode is required
            final List<T> leftBatch = new ArrayList<>(left);
            final List<T> rightBatch = new ArrayList<>(right);
            batchProcessor.accept(leftBatch);
            batchProcessor.accept(rightBatch);
            recordsProcessed.addAndGet(leftBatch.size() + rightBatch.size());
            return Collections.emptyList();
        };
    }

    public Function<List<T>, List<T>> finisher() {
        return ts -> {
            if (!ts.isEmpty()) {
                final List<T> batch = new ArrayList<>(ts);
                batchProcessor.accept(batch);
                recordsProcessed.addAndGet(batch.size());
            }
            return Collections.emptyList();
        };
    }

    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }

    public long getNumRecordsProcessed() {
        return recordsProcessed.get();
    }
}
