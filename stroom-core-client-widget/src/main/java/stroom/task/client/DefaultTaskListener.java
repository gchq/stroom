package stroom.task.client;

import com.google.gwt.event.shared.HasHandlers;

public class DefaultTaskListener implements TaskListener {

    private final HasHandlers hasHandlers;

    private final String name = this.getClass().getName();

    public DefaultTaskListener(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public void incrementTaskCount() {
        // Add the task to the map.
        TaskStartEvent.fire(hasHandlers);
    }

    @Override
    public void decrementTaskCount() {
        // Remove the task from the task count.
        TaskEndEvent.fire(hasHandlers);
    }
}
