package stroom.task.impl;

import stroom.task.api.TerminateHandler;
import stroom.task.api.TerminateHandlerFactory;

public class ThreadTerminateHandlerFactory implements TerminateHandlerFactory {

    @Override
    public TerminateHandler create() {
        return new ThreadTerminateHandler(Thread.currentThread());
    }
}
