package stroom.task.client;

import com.google.gwt.event.shared.HasHandlers;

public class DefaultTaskListener implements TaskHandlerFactory {

    private final HasHandlers hasHandlers;

    public DefaultTaskListener(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public TaskHandler createTaskHandler(final String message) {
        return new TaskHandler() {
            @Override
            public void onStart() {
                // Add the task to the map.
                TaskStartEvent.fire(hasHandlers, message);
            }

            @Override
            public void onEnd() {
                // Remove the task from the task count.
                TaskEndEvent.fire(hasHandlers);
            }
        };
    }
}
