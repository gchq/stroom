package stroom.dispatch.client;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.function.Consumer;

public interface Rest<R> {

    /**
     * Set quiet if we don't want REST call to register on the task spinner.
     *
     * @param quiet Set to true to not fire {@link stroom.task.client.TaskStartEvent}
     *              or {@link stroom.task.client.TaskEndEvent} events.
     **/
    Rest<R> quiet(boolean quiet);

    Rest<R> onSuccess(Consumer<R> consumer);

    Rest<R> onFailure(Consumer<Throwable> consumer);

    <T extends DirectRestService> T call(T service);
}
