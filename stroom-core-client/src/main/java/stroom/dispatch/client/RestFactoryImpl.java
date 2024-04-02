package stroom.dispatch.client;

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
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        final String apiUrl = hostPageBaseUrl + "/api/";
        Defaults.setServiceRoot(apiUrl);
        Defaults.setDispatcher(dispatcher);
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
        private Consumer<RestError> errorConsumer;
        private TaskListener taskListener;

        public MethodExecutorImpl(final HasHandlers hasHandlers, final T service, final Function<T, R> function) {
            this.hasHandlers = hasHandlers;
            this.service = service;
            this.function = function;
        }

        @Override
        public MethodExecutor<T, R> taskListener(final TaskListener taskListener) {
            this.taskListener = taskListener;
            return this;
        }

        @Override
        public MethodExecutor<T, R> onSuccess(final Consumer<R> resultConsumer) {
            this.resultConsumer = resultConsumer;
            return this;
        }

        @Override
        public MethodExecutor<T, R> onFailure(final Consumer<RestError> errorConsumer) {
            this.errorConsumer = errorConsumer;
            return this;
        }

        @Override
        public void exec() {
            final Consumer<RestError> errorConsumer = GwtNullSafe
                    .requireNonNullElseGet(this.errorConsumer, () -> new DefaultErrorConsumer(hasHandlers));
            final TaskListener taskListener = GwtNullSafe
                    .requireNonNullElseGet(this.taskListener, () -> new DefaultTaskListener(hasHandlers));
            final MethodCallbackImpl<R> methodCallback = new MethodCallbackImpl<>(
                    hasHandlers,
                    resultConsumer,
                    errorConsumer,
                    taskListener);
            final REST<R> rest = REST.withCallback(methodCallback);
            taskListener.incrementTaskCount();
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
        private final Consumer<RestError> errorConsumer;
        private final TaskListener taskListener;

        public MethodCallbackImpl(final HasHandlers hasHandlers,
                                  final Consumer<R> resultConsumer,
                                  final Consumer<RestError> errorConsumer,
                                  final TaskListener taskListener) {
            this.hasHandlers = hasHandlers;
            this.resultConsumer = resultConsumer;
            this.errorConsumer = errorConsumer;
            this.taskListener = taskListener;
        }

        @Override
        public void onFailure(final Method method, final Throwable throwable) {
            try {
                errorConsumer.accept(new RestError(method, throwable));
            } catch (final Throwable t) {
                new DefaultErrorConsumer(hasHandlers).accept(new RestError(method, t));
            } finally {
                taskListener.decrementTaskCount();
            }
        }

        @Override
        public void onSuccess(final Method method, final R response) {
            try {
                if (resultConsumer != null) {
                    resultConsumer.accept(response);
                }
            } catch (final Throwable t) {
                new DefaultErrorConsumer(hasHandlers).accept(new RestError(method, t));
            } finally {
                taskListener.decrementTaskCount();
            }
        }
    }
}
