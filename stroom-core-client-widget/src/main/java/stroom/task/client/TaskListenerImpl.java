package stroom.task.client;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class TaskListenerImpl implements TaskListener, HasTaskListener, HasHandlers {

    private final HasHandlers hasHandlers;
    private TaskListener taskListener = new DefaultTaskListener(this);
    private int taskCount;

    public TaskListenerImpl(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
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
        taskListener.decrementTaskCount();
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
