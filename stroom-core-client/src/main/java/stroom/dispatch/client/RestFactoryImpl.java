/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dispatch.client;

import stroom.alert.client.event.AlertEvent;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.Console;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Window.Location;
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
            final int index = hostPageBaseUrl.lastIndexOf("/");
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
        public TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
            return taskMonitorFactory(taskMonitorFactory, null);
        }

        @Override
        public TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory,
                                                     final String taskMessage) {
            return new TaskExecutorImpl<>(
                    hasHandlers,
                    service,
                    function,
                    resultConsumer,
                    errorConsumer,
                    taskMonitorFactory,
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
        private final TaskMonitorFactory taskMonitorFactory;
        private final Task task;

        public TaskExecutorImpl(final HasHandlers hasHandlers,
                                final T service,
                                final Function<T, R> function,
                                final Consumer<R> resultConsumer,
                                final RestErrorHandler errorHandler,
                                final TaskMonitorFactory taskMonitorFactory,
                                final String taskMessage) {
            this.hasHandlers = hasHandlers;
            this.service = service;
            this.function = function;
            this.resultConsumer = resultConsumer;
            this.errorHandler = errorHandler;
            this.taskMonitorFactory = taskMonitorFactory;
            this.task = new RestFactoryTask<>(service, function, taskMessage);
        }

        @Override
        public void exec() {
            final RestErrorHandler innerErrorHandler = NullSafe
                    .requireNonNullElseGet(this.errorHandler, () -> new DefaultErrorHandler(hasHandlers, null));
            final RestErrorHandler errorHandler = error -> {
                final int statusCode = NullSafe
                        .getOrElse(error, RestError::getMethod, Method::getResponse, Response::getStatusCode, -1);
                if (statusCode == Response.SC_UNAUTHORIZED) {
                    // Reload as we have been logged out.
                    Console.info(() -> "Unauthorised request, assuming user session is invalid, reloading...");
                    Location.reload();
                } else {
                    innerErrorHandler.onError(error);
                }
            };
            final TaskMonitorFactory taskMonitorFactory = NullSafe
                    .requireNonNullElseGet(this.taskMonitorFactory, () -> new DefaultTaskMonitorFactory(hasHandlers));

            final TaskMonitor taskMonitor = taskMonitorFactory.createTaskMonitor();
            final MethodCallbackImpl<R> methodCallback = new MethodCallbackImpl<>(
                    hasHandlers,
                    resultConsumer,
                    errorHandler,
                    taskMonitor,
                    task);
            final REST<R> rest = REST.withCallback(methodCallback);
            taskMonitor.onStart(task);
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
        private final TaskMonitor taskMonitor;
        private final Task task;

        public MethodCallbackImpl(final HasHandlers hasHandlers,
                                  final Consumer<R> resultConsumer,
                                  final RestErrorHandler errorHandler,
                                  final TaskMonitor taskMonitor,
                                  final Task task) {
            this.hasHandlers = hasHandlers;
            this.resultConsumer = resultConsumer;
            this.errorHandler = errorHandler;
            this.taskMonitor = taskMonitor;
            this.task = task;
        }

        @Override
        public void onSuccess(final Method method, final R response) {
            try {
                if (resultConsumer != null) {
                    resultConsumer.accept(response);
                }
            } catch (final Throwable t) {
                Console.debug(t.toString());
                if (method != null && method.getRequest() != null) {
                    Console.debug(method.getRequest().toString());
                }
                Console.error("Error processing successful response: " + response);
                AlertEvent.fireErrorFromException(hasHandlers, t, null);
            } finally {
                taskMonitor.onEnd(task);
            }
        }

        @Override
        public void onFailure(final Method method, final Throwable throwable) {
            try {
                errorHandler.onError(new RestError(method, throwable));
            } catch (final Throwable t) {
                new DefaultErrorHandler(hasHandlers, null).onError(new RestError(method, t));
            } finally {
                taskMonitor.onEnd(task);
            }
        }
    }
}
