package stroom.dispatch.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.RestService;
import stroom.alert.client.event.AlertEvent;

import java.util.function.Consumer;

public class RestApiImpl implements RestApi, HasHandlers {
    private final EventBus eventBus;

    @Inject
    public RestApiImpl(final EventBus eventBus) {
        this.eventBus = eventBus;

        String hostPageBaseUrl = GWT.getHostPageBaseURL();
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        final String apiUrl = hostPageBaseUrl + "/api/";
        Defaults.setServiceRoot(apiUrl);
    }

    @Override
    public <T extends RestService> T client(final Class<T> classLiteral) {
        return GWT.create(classLiteral);
    }

    @Override
    public <T> Callback<T> callback() {
        return new CallbackImpl<T>(this);
    }


//
//
//
//
//
//
//
//    @Override
//    public <T extends RestService> Call create(final T restService) {
//        return exec(task, null, true);
//    }
//
//    @Override
//    public <R extends SharedObject> Future<R> create(final Action<R> task, final String message) {
//        return create(task, message, true);
//    }
//
//    @Override
//    public <R extends SharedObject> Future<R> create(final Action<R> task, final boolean showWorking) {
//        return create(task, null, showWorking);
//    }
//
//    private <R extends SharedObject> Future<R> create(final Action<R> task, final String message, final boolean showWorking) {
//        if (showWorking) {
//            // Add the task to the map.
//            incrementTaskCount(message);
//        }
//
//        return dispatch(task, message, showWorking);
//    }
//
//    private <R extends SharedObject> Future<R> dispatch(final RestService restService, final String message,
//                                                        final boolean showWorking) {
//        action.setApplicationInstanceId(applicationInstanceId);
//
//        final FutureImpl<R> future = new FutureImpl<>();
//        // Set the default behaviour of the future to show an error.
//        future.onFailure(throwable -> AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable, null));
//
//        Scheduler.get().scheduleDeferred(() -> dispatchService.exec(action, new AsyncCallback<R>() {
//            @Override
//            public void onSuccess(final R result) {
//                if (showWorking) {
//                    // Remove the task from the task count.
//                    decrementTaskCount();
//                }
//
//                // Let the callback handle success.
//                handleSuccess(result);
//            }
//
//            @Override
//            public void onFailure(final Throwable throwable) {
//                if (showWorking) {
//                    // Remove the task from the task count.
//                    decrementTaskCount();
//                }
//
//                if (message != null && message.length() >= LOGIN_HTML.length() && message.contains(LOGIN_HTML)) {
//                    if (!("Logout".equalsIgnoreCase(action.getTaskName()))) {
//                        // Logout.
//                        AlertEvent.fireError(ClientDispatchAsyncImpl.this,
//                                "Your user session appears to have terminated", message, null);
//                    }
//                } else if (throwable instanceof StatusCodeException) {
//                    final StatusCodeException scEx = (StatusCodeException) throwable;
//                    if (scEx.getStatusCode() >= 100) {
//                        if (!("Logout".equalsIgnoreCase(action.getTaskName()))) {
//                            // Logout.
//                            AlertEvent.fireError(ClientDispatchAsyncImpl.this, "An error has occurred",
//                                    scEx.getStatusCode() + " - " + scEx.getMessage(), null);
//                        }
//                    }
//                }
//
//
//                handleFailure(throwable);
//
//            }
//
//            private void handleSuccess(final R result) {
//                try {
//                    future.setResult(result);
//
//                } catch (final Throwable throwable) {
//                    AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable, null);
//                }
//            }
//
//            private void handleFailure(final Throwable throwable) {
//                try {
//                    future.setThrowable(throwable);
//
//                } catch (final Throwable throwable2) {
//                    AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable2, null);
//                }
//            }
//        }));
//
//        return future;
//    }
//
//    private void incrementTaskCount(final String message) {
//        // Add the task to the map.
//        TaskStartEvent.fire(RestApiImpl.this, message);
//    }
//
//    private void decrementTaskCount() {
//        // Remove the task from the task count.
//        TaskEndEvent.fire(RestApiImpl.this);
//    }
//
//


    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    public static class CallbackImpl<T> implements Callback<T> {
        private final HasHandlers hasHandlers;
        private Consumer<T> successConsumer;
        private Consumer<Throwable> failureConsumer;

        private CallbackImpl(final HasHandlers hasHandlers) {
            this.hasHandlers = hasHandlers;
        }

        @Override
        public void onFailure(Method method, Throwable caught) {
            if (failureConsumer != null) {
                failureConsumer.accept(caught);
            } else {
                AlertEvent.fireError(hasHandlers, caught.getMessage(), null);
            }
        }

        @Override
        public void onSuccess(Method method, T result) {
            if (successConsumer != null) {
                successConsumer.accept(result);
            }
        }

        public Callback<T> onSuccess(Consumer<T> consumer) {
            successConsumer = consumer;
            return this;
        }

        public Callback<T> onFailure(Consumer<Throwable> consumer) {
            failureConsumer = consumer;
            return this;
        }
    }

    ;
}
