package stroom.explorer.client.event;

import stroom.task.client.TaskHandler;
import stroom.task.client.TaskListener;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerTaskListener implements TaskListener, HasHandlers {

    private final HasHandlers hasHandlers;

    public ExplorerTaskListener(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public TaskHandler createTaskHandler(final String message) {
        return new TaskHandler() {
            @Override
            public void onStart() {
                ExplorerStartTaskEvent.fire(ExplorerTaskListener.this);
            }

            @Override
            public void onEnd() {
                ExplorerEndTaskEvent.fire(ExplorerTaskListener.this);
            }
        };
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
