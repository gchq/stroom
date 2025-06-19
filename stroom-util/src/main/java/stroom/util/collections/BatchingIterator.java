package stroom.util.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
        return asStream(new BatchingIterator<>(originalStream.iterator(), batchSize));
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
        this.batchSize = batchSize;
        this.sourceIterator = sourceIterator;
    }

    @Override
    public boolean hasNext() {
        prepareNextBatch();
        return currentBatch != null && !currentBatch.isEmpty();
    }

    @Override
    public List<T> next() {
        return currentBatch;
    }

    private void prepareNextBatch() {
        currentBatch = new ArrayList<>(batchSize);
        while (sourceIterator.hasNext() && currentBatch.size() < batchSize) {
            currentBatch.add(sourceIterator.next());
        }
    }
}
