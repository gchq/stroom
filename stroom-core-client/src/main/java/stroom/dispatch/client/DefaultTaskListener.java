package stroom.dispatch.client;

import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskListener;
import stroom.task.client.TaskStartEvent;

import com.google.gwt.event.shared.HasHandlers;

public class DefaultTaskListener implements TaskListener {

    private final HasHandlers hasHandlers;

    DefaultTaskListener(final HasHandlers hasHandlers) {
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
