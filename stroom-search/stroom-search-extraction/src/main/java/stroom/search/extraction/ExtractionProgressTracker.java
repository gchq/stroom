package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ExtractionProgressTracker implements HasTerminate {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionProgressTracker.class);

    private final AtomicBoolean finishedAddingTasks = new AtomicBoolean();
    private final AtomicInteger tasksTotal = new AtomicInteger();
    private final AtomicInteger tasksCompleted = new AtomicInteger();
    private final HasTerminate hasTerminate;

    ExtractionProgressTracker(final HasTerminate hasTerminate) {
        this.hasTerminate = hasTerminate;
    }

    boolean isComplete() {
        LOGGER.debug(this::toString);
        return isTerminated() || (finishedAddingTasks.get() && (tasksTotal.get() == tasksCompleted.get()));
    }

    void finishedAddingTasks() {
        finishedAddingTasks.set(true);
    }

    void incrementTasksTotal() {
        tasksTotal.incrementAndGet();
    }

    void incrementTasksCompleted() {
        tasksCompleted.incrementAndGet();
    }

    @Override
    public void terminate() {
        hasTerminate.terminate();
    }

    @Override
    public boolean isTerminated() {
        return hasTerminate.isTerminated();
    }

    @Override
    public String toString() {
        return "ExtractionProgressTracker{" +
                "finishedAddingTasks=" + finishedAddingTasks +
                ", tasksTotal=" + tasksTotal +
                ", tasksCompleted=" + tasksCompleted +
                ", hasTerminate=" + hasTerminate +
                '}';
    }
}
