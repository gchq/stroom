package stroom.util.lifecycle;

public class StartupTask {
    private final int priority;

    StartupTask(final int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
