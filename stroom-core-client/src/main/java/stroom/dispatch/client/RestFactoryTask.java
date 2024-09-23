package stroom.dispatch.client;

import stroom.task.client.Task;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.function.Function;

public class RestFactoryTask<T extends DirectRestService, R> implements Task {
    private final T service;
    private final Function<T, R> function;
    private final String taskMessage;

    public RestFactoryTask(final T service, final Function<T, R> function, final String taskMessage) {
        this.service = service;
        this.function = function;
        this.taskMessage = taskMessage;
    }

    @Override
    public String toString() {
        return taskMessage;
    }
}
