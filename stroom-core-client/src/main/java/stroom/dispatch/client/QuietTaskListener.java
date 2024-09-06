package stroom.dispatch.client;

import stroom.task.client.Task;
import stroom.task.client.TaskHandler;
import stroom.task.client.TaskHandlerFactory;

public class QuietTaskListener implements TaskHandlerFactory {

    @Override
    public TaskHandler createTaskHandler() {
        return new TaskHandler() {
            @Override
            public void onStart(final Task task) {

            }

            @Override
            public void onEnd(final Task task) {

            }
        };
    }
}
