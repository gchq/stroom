package stroom.explorer.client.event;

import stroom.task.client.Task;
import stroom.task.client.TaskHandler;
import stroom.task.client.TaskHandlerFactory;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerTaskListener implements TaskHandlerFactory, HasHandlers {

    private final HasHandlers hasHandlers;

    public ExplorerTaskListener(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public TaskHandler createTaskHandler() {
        return new TaskHandler() {
            @Override
            public void onStart(final Task task) {
                ExplorerStartTaskEvent.fire(ExplorerTaskListener.this, task);
            }

            @Override
            public void onEnd(final Task task) {
                ExplorerEndTaskEvent.fire(ExplorerTaskListener.this, task);
            }
        };
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
