/*
 * Copyright 2017 Crown Copyright
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

package stroom.util.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class for converting a stream of T into batches of {@link List}
 *
 * @param <T> The type of object in the stream
 */
public class BatchingIterator<T> implements Iterator<List<T>> {

    /**
     * Given a {@link Stream}, convert it to a {@link Stream} of batches no greater than the
     * batchSize.
     *
     * @param originalStream to convert
     * @param batchSize      maximum size of a batch
     * @param <T>            type of items in the stream
     * @return a stream of batches taken sequentially from the original stream
     */
    public static <T> Stream<List<T>> batchedStreamOf(final Stream<T> originalStream, final int batchSize) {
        Objects.requireNonNull(originalStream, "Null original stream");
        validateBatchSize(batchSize);
        return asStream(new BatchingIterator<>(originalStream.iterator(), batchSize))
                .onClose(originalStream::close);
    }

    /**
     * Given a {@link java.util.Collection}, convert it to a stream of batches no greater than the
     * batchSize.
     *
     * @param originalCollection to convert
     * @param batchSize          maximum size of a batch
     * @param <T>                type of items in the stream
     * @return a stream of batches taken sequentially from the original stream
     */
    public static <T> Stream<List<T>> batchedStreamOf(final Collection<T> originalCollection, final int batchSize) {
        Objects.requireNonNull(originalCollection, "Null original collection");
        validateBatchSize(batchSize);
        return asStream(new BatchingIterator<>(originalCollection.iterator(), batchSize));
    }

    private static <T> Stream<T> asStream(final Iterator<T> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    private final int batchSize;
    private List<T> currentBatch;
    private final Iterator<T> sourceIterator;

    private BatchingIterator(final Iterator<T> sourceIterator, final int batchSize) {
        Objects.requireNonNull(sourceIterator, "Null source iterator");
        validateBatchSize(batchSize);
        this.batchSize = batchSize;
        this.sourceIterator = sourceIterator;
    }

    @Override
    public boolean hasNext() {
        if (currentBatch == null) {
            prepareNextBatch();
        }
        return currentBatch != null && !currentBatch.isEmpty();
    }

    @Override
    public List<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        final List<T> nextBatch = currentBatch;
        currentBatch = null;
        return nextBatch;
    }

    private void prepareNextBatch() {
        final List<T> nextBatch = new ArrayList<>();
        while (sourceIterator.hasNext() && nextBatch.size() < batchSize) {
            nextBatch.add(sourceIterator.next());
        }
        currentBatch = nextBatch.isEmpty()
                ? null
                : nextBatch;
    }

    private static void validateBatchSize(final int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be greater than zero");
        }
    }
}
