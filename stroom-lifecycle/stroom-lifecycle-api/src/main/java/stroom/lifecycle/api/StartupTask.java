package stroom.lifecycle.api;

public class StartupTask {
    private final int priority;

    StartupTask(final int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    // No equals/hashcode as guice MultiBinder will use object identity
}
