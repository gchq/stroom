package stroom.dispatch.client;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.function.Consumer;
import java.util.function.Function;

public interface RestFactory {

    <T extends DirectRestService> Resource<T> create(T service);

    interface Resource<T extends DirectRestService> {

        <R> MethodExecutor<T, R> method(Function<T, R> function);

        MethodExecutor<T, Void> call(Consumer<T> consumer);
    }

    interface MethodExecutor<T extends DirectRestService, R> {

        /**
         * Set quiet if we don't want REST call to register on the task spinner.
         *
         * @param quiet Set to true to not fire {@link stroom.task.client.TaskStartEvent}
         *              or {@link stroom.task.client.TaskEndEvent} events.
         **/
        MethodExecutor<T, R> quiet(boolean quiet);

        MethodExecutor<T, R> onSuccess(Consumer<R> consumer);

        MethodExecutor<T, R> onFailure(Consumer<Throwable> consumer);

        void exec();
    }

    String getImportFileURL();
}
