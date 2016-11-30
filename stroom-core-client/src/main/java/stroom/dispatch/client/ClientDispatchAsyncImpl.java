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
import stroom.dispatch.shared.DispatchServiceAsync;
import stroom.entity.shared.Action;
import stroom.security.client.ClientSecurityContext;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.task.shared.RefreshAction;
import stroom.util.client.RandomId;
import stroom.util.shared.SharedObject;
import stroom.util.shared.SharedString;

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
                        execute(action, new AsyncCallbackAdaptor<SharedString>() {
                            @Override
                            public void onSuccess(final SharedString result) {
                                if (result != null) {
                                    AlertEvent.fireWarn(ClientDispatchAsyncImpl.this, result.toString(), () -> refreshing = false);
                                } else {
                                    refreshing = false;
                                }
                            }

                            @Override
                            public void onFailure(final Throwable caught) {
                                refreshing = false;
                            }
                        });
                    }
                }
            }
        };
        // Refresh all actions on the server every minute so that they
        // don't die.
        refreshTimer.scheduleRepeating(ONE_MINUTE);

        // realService = GWT.create(DispatchService.class);
        final String endPointName = GWT.getHostPageBaseURL() + "dispatch.rpc";
        final ServiceDefTarget target = (ServiceDefTarget) dispatchService;
        target.setServiceEntryPoint(endPointName);
    }

    @Override
    public <R extends SharedObject> void execute(final Action<R> task, final AsyncCallbackAdaptor<R> callback) {
        execute(task, null, true, callback);
    }

    @Override
    public <R extends SharedObject> void execute(final Action<R> task, final String message,
                                                 final AsyncCallbackAdaptor<R> callback) {
        execute(task, message, true, callback);
    }

    @Override
    public <R extends SharedObject> void execute(final Action<R> task, final boolean showWorking,
                                                 final AsyncCallbackAdaptor<R> callback) {
        execute(task, null, showWorking, callback);
    }

    private <R extends SharedObject> void execute(final Action<R> task, final String message, final boolean showWorking,
                                                  final AsyncCallbackAdaptor<R> callback) {
        if (showWorking) {
            // Add the task to the map.
            incrementTaskCount(message);
        }

        Scheduler.get().scheduleDeferred(() -> dispatch(task, message, showWorking, callback));
    }

    private <R extends SharedObject> void dispatch(final Action<R> action, final String message,
                                                   final boolean showWorking, final AsyncCallbackAdaptor<R> callback) {
        action.setApplicationInstanceId(applicationInstanceId);
        dispatchService.exec(action, new AsyncCallback<R>() {
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
                            AlertEvent.fireError(ClientDispatchAsyncImpl.this, "An error has occured",
                                    scEx.getStatusCode() + " - " + scEx.getMessage(), null);
                        }
                    }
                } else {
                    // If the callback exclusively handles failure then let it
                    // do so.
                    if (callback != null && callback.handlesFailure()) {
                        callback.onFailure(throwable);
                    } else {
                        // Otherwise show the failure message and defer any
                        // other failure handling to the callback.
                        showFailure(throwable);
                    }
                }
            }

            private void showFailure(final Throwable throwable) {
                // Show the failure message if the callback does not
                // deal with the failure.
                if (callback == null || !callback.handlesFailure()) {
                    AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable, () -> {
                        // Let the callback handle any other aspect
                        // of the failure.
                        handleFailure(throwable);
                    });
                }
            }

            private void handleSuccess(final R result) {
                if (callback != null) {
                    try {
                        callback.onSuccess(result);

                    } catch (final Throwable throwable) {
                        AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable, null);
                    }
                }
            }

            private void handleFailure(final Throwable throwable) {
                if (callback != null) {
                    try {
                        callback.onFailure(throwable);

                    } catch (final Throwable throwable2) {
                        AlertEvent.fireErrorFromException(ClientDispatchAsyncImpl.this, throwable2, null);
                    }
                }
            }
        });
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
