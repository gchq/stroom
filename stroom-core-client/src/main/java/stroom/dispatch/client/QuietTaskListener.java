package stroom.dispatch.client;

import stroom.task.client.TaskHandler;
import stroom.task.client.TaskHandlerFactory;

public class QuietTaskListener implements TaskHandlerFactory {

    @Override
    public TaskHandler createTaskHandler(final String message) {
        return new TaskHandler() {
            @Override
            public void onStart() {

            }

            @Override
            public void onEnd() {

            }
        };
    }
}
