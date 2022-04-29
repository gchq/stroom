package stroom.proxy.repo.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadQueue<T> {

    private final int batchSize;
    private volatile List<T> readQueue = Collections.emptyList();
    private final RecordReader<T> recordReader;

    private volatile long readPos;

    public ReadQueue(final RecordReader<T> recordReader,
                     final int batchSize) {
        this.recordReader = recordReader;
        this.batchSize = batchSize;
    }

    public void fill() {
        if (readQueue.size() != 0) {
            throw new RuntimeException("Expected empty queue");
        }
        readQueue = new ArrayList<>();
        readPos = recordReader.read(readPos, batchSize, readQueue);
    }

    public Batch<T> getBatch() {
        final List<T> result = readQueue;
        readQueue = Collections.emptyList();
        return new Batch(result, result.size() == batchSize);
    }

    public int size() {
        return readQueue.size();
    }

    public void clear() {
        readQueue = Collections.emptyList();
        readPos = 0;
    }
}