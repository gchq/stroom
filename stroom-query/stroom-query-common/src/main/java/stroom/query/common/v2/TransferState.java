package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class TransferState {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TransferState.class);

    private final AtomicBoolean terminated = new AtomicBoolean();
    private volatile Thread thread;

    public boolean isTerminated() {
        return terminated.get();
    }

    public synchronized void terminate() {
        terminated.set(true);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public synchronized void setThread(final Thread thread) {
        this.thread = thread;
        if (terminated.get()) {
            if (thread != null) {
                thread.interrupt();
            } else if (Thread.interrupted()) {
                LOGGER.debug(() -> "Cleared interrupt state");
            }
        }
    }
}
