package stroom.dispatch.client;

import stroom.task.client.DefaultTaskListener;
import stroom.task.client.Task;
import stroom.task.client.TaskHandler;
import stroom.task.client.TaskHandlerFactory;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.DirectRestService;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.REST;

import java.util.function.Consumer;
import java.util.function.Function;

class RestFactoryImpl implements RestFactory, HasHandlers {

    private final EventBus eventBus;

    @Inject
    public RestFactoryImpl(final EventBus eventBus, final Dispatcher dispatcher) {
        this.eventBus = eventBus;

        String hostPageBaseUrl = GWT.getHostPageBaseURL();
        hostPageBaseUrl = trimPath(hostPageBaseUrl);
        hostPageBaseUrl = trimPath(hostPageBaseUrl);
        final String apiUrl = hostPageBaseUrl + "/api/";
        Defaults.setServiceRoot(apiUrl);
        Defaults.setDispatcher(dispatcher);
    }

    private String trimPath(final String hostPageBaseUrl) {
        int start = hostPageBaseUrl.indexOf("//");
        if (start != -1) {
            start++;
            int index = hostPageBaseUrl.lastIndexOf("/");
            if (index != -1 && start != index) {
                return hostPageBaseUrl.substring(0, index);
            }
        }
        return hostPageBaseUrl;
    }

    @Override
    public <T extends DirectRestService> Resource<T> create(final T service) {
        return new ResourceImpl<>(this, service);
    }

    private static class ResourceImpl<T extends DirectRestService> implements Resource<T> {

        private final HasHandlers hasHandlers;
        private final T service;

        public ResourceImpl(final HasHandlers hasHandlers,
                            final T service) {
            this.hasHandlers = hasHandlers;
            this.service = service;
        }

        @Override
        public <R> MethodExecutor<T, R> method(final Function<T, R> function) {
            return new MethodExecutorImpl<>(hasHandlers, service, function);
        }

        @Override
        public MethodExecutor<T, Void> call(final Consumer<T> consumer) {
            final Function<T, Void> function = t -> {
                consumer.accept(t);
                return null;
            };
            return new MethodExecutorImpl<>(hasHandlers, service, function);
        }
    }

    private static class MethodExecutorImpl<T extends DirectRestService, R> implements MethodExecutor<T, R> {

        private final HasHandlers hasHandlers;
        private final T service;
        private final Function<T, R> function;

        private Consumer<R> resultConsumer;
        private RestErrorHandler errorConsumer;

        public MethodExecutorImpl(final HasHandlers hasHandlers,
                                  final T service,
                                  final Function<T, R> function) {
            this.hasHandlers = hasHandlers;
            this.service = service;
            this.function = function;
        }

        @Override
        public TaskExecutor<T, R> taskHandlerFactory(final TaskHandlerFactory taskHandlerFactory) {
            return taskHandlerFactory(taskHandlerFactory, null);
        }

        @Override
        public TaskExecutor<T, R> taskHandlerFactory(final TaskHandlerFactory taskHandlerFactory,
                                                     final String taskMessage) {
            return new TaskExecutorImpl<>(
                    hasHandlers,
                    service,
                    function,
                    resultConsumer,
                    errorConsumer,
                    taskHandlerFactory,
                    taskMessage);
        }

        @Override
        public MethodExecutor<T, R> onSuccess(final Consumer<R> resultConsumer) {
            this.resultConsumer = resultConsumer;
            return this;
        }

        @Override
        public MethodExecutor<T, R> onFailure(final RestErrorHandler errorHandler) {
            this.errorConsumer = errorHandler;
            return this;
        }
    }

    private static class TaskExecutorImpl<T extends DirectRestService, R> implements TaskExecutor<T, R> {

        private final HasHandlers hasHandlers;
        private final T service;
        private final Function<T, R> function;

        private final Consumer<R> resultConsumer;
        private final RestErrorHandler errorHandler;
        private final TaskHandlerFactory taskHandlerFactory;
        private final Task task;

        public TaskExecutorImpl(final HasHandlers hasHandlers,
                                final T service,
                                final Function<T, R> function,
                                final Consumer<R> resultConsumer,
                                final RestErrorHandler errorHandler,
                                final TaskHandlerFactory taskHandlerFactory,
                                final String taskMessage) {
            this.hasHandlers = hasHandlers;
            this.service = service;
            this.function = function;
            this.resultConsumer = resultConsumer;
            this.errorHandler = errorHandler;
            this.taskHandlerFactory = taskHandlerFactory;
            this.task = new RestFactoryTask<>(service, function, taskMessage);
        }

        @Override
        public void exec() {
            final RestErrorHandler errorHandler = GwtNullSafe
                    .requireNonNullElseGet(this.errorHandler, () -> new DefaultErrorHandler(hasHandlers, null));
            final TaskHandlerFactory taskHandlerFactory = GwtNullSafe
                    .requireNonNullElseGet(this.taskHandlerFactory, () -> new DefaultTaskListener(hasHandlers));

            final TaskHandler taskHandler = taskHandlerFactory.createTaskHandler();
            final MethodCallbackImpl<R> methodCallback = new MethodCallbackImpl<>(
                    hasHandlers,
                    resultConsumer,
                    errorHandler,
                    taskHandler,
                    task);
            final REST<R> rest = REST.withCallback(methodCallback);
            taskHandler.onStart(task);
            function.apply(rest.call(service));
        }
    }


    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    private static class MethodCallbackImpl<R> implements MethodCallback<R> {

        private final HasHandlers hasHandlers;
        private final Consumer<R> resultConsumer;
        private final RestErrorHandler errorHandler;
        private final TaskHandler taskHandler;
        private final Task task;

        public MethodCallbackImpl(final HasHandlers hasHandlers,
                                  final Consumer<R> resultConsumer,
                                  final RestErrorHandler errorHandler,
                                  final TaskHandler taskHandler,
                                  final Task task) {
            this.hasHandlers = hasHandlers;
            this.resultConsumer = resultConsumer;
            this.errorHandler = errorHandler;
            this.taskHandler = taskHandler;
            this.task = task;
        }

        @Override
        public void onFailure(final Method method, final Throwable throwable) {
            try {
                errorHandler.onError(new RestError(method, throwable));
            } catch (final Throwable t) {
                new DefaultErrorHandler(hasHandlers, null).onError(new RestError(method, t));
            } finally {
                taskHandler.onEnd(task);
            }
        }

        @Override
        public void onSuccess(final Method method, final R response) {
            try {
                if (resultConsumer != null) {
                    resultConsumer.accept(response);
                }
            } catch (final Throwable t) {
                new DefaultErrorHandler(hasHandlers, null).onError(new RestError(method, t));
            } finally {
                taskHandler.onEnd(task);
            }
        }
    }
}
