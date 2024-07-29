package stroom.explorer.client.event;

import stroom.task.client.TaskListener;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerTaskListener implements TaskListener, HasHandlers {

    private final HasHandlers hasHandlers;

    public ExplorerTaskListener(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public void incrementTaskCount() {
        ExplorerStartTaskEvent.fire(this);
    }

    @Override
    public void decrementTaskCount() {
        ExplorerEndTaskEvent.fire(this);
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
