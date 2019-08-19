package stroom.search.extraction;

import java.util.concurrent.atomic.AtomicLong;

public class ExtractionTaskCompletionStatus extends CompletionStatusImpl {
    private final AtomicLong tasksCreated = new AtomicLong();
    private final AtomicLong successfulExtractions = new AtomicLong();
    private final AtomicLong failedExtractions = new AtomicLong();

    public AtomicLong getTasksCreated() {
        return tasksCreated;
    }

    public AtomicLong getSuccessfulExtractions() {
        return successfulExtractions;
    }

    public AtomicLong getFailedExtractions() {
        return failedExtractions;
    }

    @Override
    public String toString() {
        return super.toString() + "\nExtractionTaskCompletionStatus{" +
                "tasksCreated=" + tasksCreated +
                ", successfulExtractions=" + successfulExtractions +
                ", failedExtractions=" + failedExtractions +
                '}';
    }
}
