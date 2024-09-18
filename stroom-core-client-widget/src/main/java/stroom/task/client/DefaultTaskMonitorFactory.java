package stroom.task.client;

import com.google.gwt.event.shared.HasHandlers;

public class DefaultTaskMonitorFactory implements TaskMonitorFactory {

    private final HasHandlers hasHandlers;

    public DefaultTaskMonitorFactory(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                // Add the task to the map.
                TaskStartEvent.fire(hasHandlers, task);
            }

            @Override
            public void onEnd(final Task task) {
                // Remove the task from the task count.
                TaskEndEvent.fire(hasHandlers, task);
            }
        };
    }
}
