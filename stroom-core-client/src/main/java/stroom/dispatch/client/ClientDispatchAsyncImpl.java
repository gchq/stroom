/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.shared.Action;
import stroom.security.client.ClientSecurityContext;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.task.shared.RefreshAction;
import stroom.util.client.RandomId;
import stroom.util.shared.SharedObject;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import javax.inject.Provider;

public class ClientDispatchAsyncImpl implements ClientDispatchAsync, HasHandlers {
    private static final String LOGIN_HTML = "<title>Login</title>";
    private static final int ONE_MINUTE = 1000 * 60;

    private final EventBus eventBus;
    private final DispatchServiceAsync dispatchService;
    private final String applicationInstanceId;
    private boolean refreshing = false;

    @Inject
    public ClientDispatchAsyncImpl(final EventBus eventBus, final Provider<ClientSecurityContext> securityContextProvider,
                                   final DispatchServiceAsync dispatchService) {
        this.eventBus = eventBus;
        this.dispatchService = dispatchService;
        this.applicationInstanceId = RandomId.createDiscrimiator();

        final Timer refreshTimer = new Timer() {
            @Override
            public void run() {
                if (!refreshing) {
                    final ClientSecurityContext securityContext = securityContextProvider.get();
                    if (securityContext != null && securityContext.isLoggedIn()) {
                        refreshing = true;
                        final RefreshAction action = new RefreshAction();
                        action.setApplicationInstanceId(applicationInstanceId);
                        exec(action).onSuccess(result -> {
                            if (result != null) {
                                AlertEvent.fireWarn(ClientDispatchAsyncImpl.this, result.toString(), () -> refreshing = false);
                            } else {
                                refreshing = false;
                            }
                        }).onFailure(throwable -> refreshing = false);
                    }
                }
            }
        };
        // Refresh all actions on the server every minute so that they
        // don't die.
        refreshTimer.scheduleRepeating(ONE_MINUTE);

        // realService = GWT.create(DispatchService.class);
        final String endPointName = GWT.getModuleBaseURL() + "dispatch.rpc";
        final ServiceDefTarget target = (ServiceDefTarget) dispatchService;
        target.setServiceEntryPoint(endPointName);
    }

    @Override
    public <R extends SharedObject> Future<R> exec(final Action<R> task) {
        return exec(task, null, true);
    }

    @Override
    public <R extends SharedObject> Future<R> exec(final Action<R> task, final String message) {
        return exec(task, message, true);
    }

    @Override
    public <R extends SharedObject> Future<R> exec(final Action<R> task, final boolean showWorking) {
        return exec(task, null, showWorking);
    }

    private <R extends SharedObject> Future<R> exec(final Action<R> task, final String message, final boolean showWorking) {
        if (showWorking) {
            // Add the task to the map.
            incrementTaskCount(message);
        }

        return dispatch(task, message, showWorking);
    }

    private <R extends SharedObject> Future<R> dispatch(final Action<R> action, final String message,
                                                        final boolean showWorking) {
        action.setApplicationInstanceId(applicationInstanceId);

        final FutureImpl<R> future = new FutureImpl<>();
        // Set the default behaviour of the future to show an error.
        future.onFailure(throwable -> AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable, null));

        Scheduler.get().scheduleDeferred(() -> dispatchService.exec(action, new AsyncCallback<R>() {
            @Override
            public void onSuccess(final R result) {
                if (showWorking) {
                    // Remove the task from the task count.
                    decrementTaskCount();
                }

                // Let the callback handle success.
                handleSuccess(result);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                if (showWorking) {
                    // Remove the task from the task count.
                    decrementTaskCount();
                }

                if (message != null && message.length() >= LOGIN_HTML.length() && message.contains(LOGIN_HTML)) {
                    if (!("Logout".equalsIgnoreCase(action.getTaskName()))) {
                        // Logout.
                        AlertEvent.fireError(ClientDispatchAsyncImpl.this,
                                "Your user session appears to have terminated", message, null);
                    }
                } else if (throwable instanceof StatusCodeException) {
                    final StatusCodeException scEx = (StatusCodeException) throwable;
                    if (scEx.getStatusCode() >= 100) {
                        if (!("Logout".equalsIgnoreCase(action.getTaskName()))) {
                            // Logout.
                            AlertEvent.fireError(ClientDispatchAsyncImpl.this, "An error has occurred",
                                    scEx.getStatusCode() + " - " + scEx.getMessage(), null);
                        }
                    }
                }

                handleFailure(throwable);
            }

            private void handleSuccess(final R result) {
                try {
                    future.setResult(result);

                } catch (final Throwable throwable) {
                    AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable, null);
                }
            }

            private void handleFailure(final Throwable throwable) {
                try {
                    future.setThrowable(throwable);

                } catch (final Throwable throwable2) {
                    AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable2, null);
                }
            }
        }));

        return future;
    }

    private void incrementTaskCount(final String message) {
        // Add the task to the map.
        TaskStartEvent.fire(ClientDispatchAsyncImpl.this, message);
    }

    private void decrementTaskCount() {
        // Remove the task from the task count.
        TaskEndEvent.fire(ClientDispatchAsyncImpl.this);
    }

    @Override
    public String getImportFileURL() {
        return GWT.getModuleBaseURL() + "importfile.rpc";
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
