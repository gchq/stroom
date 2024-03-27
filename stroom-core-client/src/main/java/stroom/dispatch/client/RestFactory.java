package stroom.dispatch.client;

import stroom.util.shared.ResultPage;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface RestFactory {

    <T extends DirectRestService> Resource<T> resource(T service);

    interface Resource<T extends DirectRestService> {

        <R> RestExecutor<T, R> method(Function<T, R> function);

        <R> RestExecutor<T, R> call(Consumer<T> consumer);
    }

    interface RestExecutor<T extends DirectRestService, R> {

        /**
         * Set quiet if we don't want REST call to register on the task spinner.
         *
         * @param quiet Set to true to not fire {@link stroom.task.client.TaskStartEvent}
         *              or {@link stroom.task.client.TaskEndEvent} events.
         **/
        RestExecutor<T, R> quiet(boolean quiet);

        RestExecutor<T, R> onSuccess(Consumer<R> consumer);

        RestExecutor<T, R> onFailure(Consumer<Throwable> consumer);

        void exec();
    }

    /**
     * Create a {@link Rest} for a simple return type with no generics,
     * e.g. {@link String} or {@link stroom.docref.DocRef}.
     */
    @Deprecated
    <R> Rest<R> forType(final Class<R> type);

    /**
     * Create a {@link Rest} for a {@link ResultPage} return type with a given
     * non-generic item type, e.g. {@link String}.
     */
    @Deprecated
    <T> Rest<ResultPage<T>> forResultPageOf(final Class<T> itemType);

    String getImportFileURL();
}