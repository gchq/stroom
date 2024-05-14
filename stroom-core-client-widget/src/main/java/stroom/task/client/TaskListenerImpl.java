package stroom.task.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class TaskListenerImpl implements TaskListener, HasTaskListener, HasHandlers {

    private final HasHandlers hasHandlers;
    private TaskListener taskListener = new DefaultTaskListener(this);
    private int taskCount;

    private String name;

    public TaskListenerImpl(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
        name = hasHandlers.getClass().getName();
    }

    @Override
    public synchronized void setTaskListener(final TaskListener taskListener) {
        if (taskListener != this.taskListener) {
            // Transfer task counts to the new listener.
            for (int i = 0; i < taskCount; i++) {
                taskListener.incrementTaskCount();
                this.taskListener.decrementTaskCount();
            }
            this.taskListener = taskListener;
        }
    }

    @Override
    public synchronized void incrementTaskCount() {
        taskCount++;
        taskListener.incrementTaskCount();
    }

    @Override
    public synchronized void decrementTaskCount() {
        taskCount--;

        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        taskListener.decrementTaskCount();
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
