package stroom.search.coprocessor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletionState {
    private final AtomicBoolean complete = new AtomicBoolean();
    private final CountDownLatch completeLatch = new CountDownLatch(1);

    public boolean isComplete() {
        return complete.get();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return completeLatch.await(timeout, unit);
    }

    public void complete() {
        if (complete.compareAndSet(false, true)) {
            completeLatch.countDown();
        }
    }
}
