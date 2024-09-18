package stroom.explorer.client.event;

import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerTaskMonitorFactory implements TaskMonitorFactory, HasHandlers {

    private final HasHandlers hasHandlers;

    public ExplorerTaskMonitorFactory(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                ExplorerStartTaskEvent.fire(ExplorerTaskMonitorFactory.this, task);
            }

            @Override
            public void onEnd(final Task task) {
                ExplorerEndTaskEvent.fire(ExplorerTaskMonitorFactory.this, task);
            }
        };
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
