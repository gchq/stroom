package stroom.search.impl.shard;

import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DocIdQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocIdQueue.class);

    private final ArrayBlockingQueue<Integer> queue;
    private final AtomicBoolean complete = new AtomicBoolean();

    public DocIdQueue(final int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public Integer next() throws CompleteException {
        try {
            final boolean complete = this.complete.get();
            final Integer docId = queue.poll(1, TimeUnit.SECONDS);
            if (docId == null && complete) {
                throw new CompleteException();
            }
            return docId;
        } catch (final InterruptedException e) {
            // We shouldn't get interrupted.
            LOGGER.error(e::getMessage, e);
            throw UncheckedInterruptedException.create(e);
        }
    }

    public boolean offer(final Integer docId,
                         final long timeout,
                         final TimeUnit unit) {
        try {
            return queue.offer(docId, timeout, unit);
        } catch (final InterruptedException e) {
            // We shouldn't get interrupted.
            LOGGER.error(e::getMessage, e);
            throw UncheckedInterruptedException.create(e);
        }
    }

    public void complete() {
        complete.set(true);
    }
}
