package stroom.dispatch.client;

import stroom.task.client.TaskListener;

public class QuietTaskListener implements TaskListener {

    private final String name = this.getClass().getName();

    @Override
    public void incrementTaskCount() {

    }

    @Override
    public void decrementTaskCount() {

    }
}
