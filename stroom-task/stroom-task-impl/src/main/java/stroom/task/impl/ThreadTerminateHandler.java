package stroom.task.impl;

import stroom.task.api.TerminateHandler;

public class ThreadTerminateHandler implements TerminateHandler {

    private final Thread thread;

    public ThreadTerminateHandler(final Thread thread) {
        this.thread = thread;
    }

    @Override
    public void onTerminate() {
        thread.interrupt();
    }
}
