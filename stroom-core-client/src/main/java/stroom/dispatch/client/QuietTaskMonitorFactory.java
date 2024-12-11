package stroom.dispatch.client;

import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

public class QuietTaskMonitorFactory implements TaskMonitorFactory {

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {

            }

            @Override
            public void onEnd(final Task task) {

            }
        };
    }
}
