package stroom.instance.client;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.instance.shared.ApplicationInstanceInfo;
import stroom.instance.shared.ApplicationInstanceResource;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.LogoutEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.Console;
import stroom.websocket.client.CloseEvent;
import stroom.websocket.client.ErrorEvent;
import stroom.websocket.client.MessageEvent;
import stroom.websocket.client.OpenEvent;
import stroom.websocket.client.WebSocket;
import stroom.websocket.client.WebSocketListener;
import stroom.websocket.client.WebSocketUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Timer;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientApplicationInstance implements HasHandlers {

    private static final ApplicationInstanceResource APPLICATION_INSTANCE_RESOURCE =
            GWT.create(ApplicationInstanceResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;

    private ApplicationInstanceInfo applicationInstanceInfo;
    private WebSocket webSocket;
    private boolean destroy;
    private boolean showingError;
    private boolean shouldShowError = true;
    private Timer keepAliveTimer = null;

    @Inject
    public ClientApplicationInstance(final EventBus eventBus,
                                     final RestFactory restFactory,
                                     final UiConfigCache uiConfigCache) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;

        eventBus.addHandler(CurrentUserChangedEvent.getType(), event -> register());

        // Ensure the app instance is destroyed when the users logout, close
        // the browser tab or close the browser
        eventBus.addHandler(LogoutEvent.getType(), event -> destroy());
        addNativeEventListener("unload", event -> destroy());
    }

    public String getInstanceUuid() {
        // If for any reason we don't have a web socket connection then initiate one
        if (webSocket == null) {
            tryWebSocket();
        }
        if (applicationInstanceInfo == null) {
            error("Null application instance uuid");
            return null;
        }
        return applicationInstanceInfo.getUuid();
    }

    public void register() {
        final Rest<ApplicationInstanceInfo> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    Console.log("Registered application instance ID " + result.getUuid()
                            + " for user " + result.getUserId());
                    applicationInstanceInfo = result;
                    // Use a web socket to keep the instance alive.
                    tryWebSocket();
                })
                .onFailure(throwable -> {
                    error("Unable to register application instance", throwable.getMessage());
                    // We only want to see
                    shouldShowError = false;
                })
                .call(APPLICATION_INSTANCE_RESOURCE)
                .register();
    }

    private void destroy() {
        destroy = true;
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    if (result) {
                        Console.log("Destroyed application instance " + applicationInstanceInfo.getUuid()
                                + " for user " + applicationInstanceInfo.getUserId());
                    } else {
                        Console.log("Unable to destroy application instance");
                    }
                })
                .onFailure(throwable ->
                        error("Unable to destroy application instance", throwable.getMessage()))
                .call(APPLICATION_INSTANCE_RESOURCE)
                .destroy(applicationInstanceInfo);
        keepAliveTimer.cancel();
        keepAliveTimer = null;
        webSocket.close();
        webSocket = null;
    }

    private void tryWebSocket() {
        // Make sure the app instance has not been destroyed by e.g. browser/tab closure or logout
        if (!destroy) {
            final String url = WebSocketUtil.createWebSocketUrl("/application-instance");
            Console.log("Using Web Socket URL: " + url);
            webSocket = new WebSocket(url, new WebSocketListener() {
                @Override
                public void onOpen(final OpenEvent event) {
                    // do something on open
                    Console.log("Opening web socket at " + url);
                    if (!destroy && webSocket != null) {
                        if (applicationInstanceInfo == null) {
                            error("No application instance is registered");
                        } else {
                            webSocket.send(applicationInstanceInfo.getUuid());
                            initiateWebSocketKeepAlive();
                        }
                    }
                }

                @Override
                public void onClose(final CloseEvent event) {
                    // do something on close
                    Console.log("Closing web socket at " + url);

                    // reset the flag so we see errors on reconnection
                    shouldShowError = true;
                    webSocket = null;
                    keepAliveTimer.cancel();
                    keepAliveTimer = null;
                    // Try to reopen the web socket if closing it was unexpected.
                    if (!destroy) {
                        GWT.log("Scheduling timed call to tryWebSocket");
                        new Timer() {
                            @Override
                            public void run() {
                                GWT.log("Timed call to tryWebSocket");
                                tryWebSocket();
                            }
                        }.schedule(10_000);
                    }
                }

                @Override
                public void onMessage(final MessageEvent event) {
                }

                @Override
                public void onError(final ErrorEvent event) {
                    Console.log("Error on web socket at " + url);
                    // Error may be due to the ws connection dying which is semi expected
                    // so don't always show
                    if (shouldShowError) {
                        error("Error on web socket trying to keep application instance alive",
                                "Error on web socket at " + url + "\n" + event.getReason());
                    }
                }
            });
        }
    }

    public void initiateWebSocketKeepAlive() {
        uiConfigCache.get()
                .onSuccess(uiConfig -> {
                    final int intervalMs = uiConfig.getApplicationInstanceKeepAliveIntervalMs();
                    if (intervalMs > 0) {
                        // Send a message periodically to keep the web socket open
                        // Worth noting that Chrome will throttle timers on inactive tabs to a max
                        // frequency of 1/min. If low on memory it can also discard inactive tabs.
                        keepAliveTimer = new Timer() {
                            @Override
                            public void run() {
                                GWT.log("Sending timed keep alive message");
                                if (webSocket != null && applicationInstanceInfo != null) {
                                    webSocket.send(applicationInstanceInfo.getUuid());
                                }
                            }
                        };
                        keepAliveTimer.scheduleRepeating(intervalMs);
                    }
                });
    }

    public void error(final String message) {
        Console.log("Error: " + message);
        if (!showingError) {
            showingError = true;
            AlertEvent.fireError(this,
                    message,
                    () -> showingError = false);
        }
    }

    public void error(final String message, final String detail) {
        Console.log("Error: " + message + "\n" + detail);
        if (!showingError) {
            showingError = true;
            AlertEvent.fireError(this,
                    message,
                    detail,
                    () -> showingError = false);
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static native void addNativeEventListener(final String event,
                                                      final EventListener listener) /*-{
        $wnd.addEventListener(event, $entry(function(e) {
            listener.@com.google.gwt.user.client.EventListener::onBrowserEvent(Lcom/google/gwt/user/client/Event;)(e);
        }));
    }-*/;
}
