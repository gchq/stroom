package stroom.lifecycle.api;

public class ShutdownTask {
    private final int priority;

    ShutdownTask(final int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
