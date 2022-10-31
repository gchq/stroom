package stroom.instance.client;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.instance.shared.ApplicationInstanceInfo;
import stroom.instance.shared.ApplicationInstanceResource;
import stroom.instance.shared.DestroyRequest;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.LogoutEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.Console;
import stroom.util.shared.IsWebSocket;
import stroom.util.shared.WebSocketMessage;
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
    private boolean isOpen;
    private Timer keepAliveTimer;
    private int errorCount;

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
        eventBus.addHandler(LogoutEvent.getType(), event ->
                destroy("Logout"));
        addNativeEventListener("unload", event ->
                destroy("Unload"));
    }

    public String getInstanceUuid() {
        if (applicationInstanceInfo == null) {
            fireErrorEvent("Null application instance uuid");
            return null;
        }
        return applicationInstanceInfo.getUuid();
    }

    private void register() {
        Console.log("Registering application instance ID");
        final Rest<ApplicationInstanceInfo> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    Console.log("Registered application instance ID " + result.getUuid()
                            + " for user " + result.getUserId());
                    applicationInstanceInfo = result;

                    if (keepAliveTimer == null) {
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
                                                keepAliveWebsocket(intervalMs);
                                            }
                                        };
                                        Console.log("Initiating timed keep alive (interval: " + intervalMs + "ms)");
                                        keepAliveTimer.scheduleRepeating(intervalMs);
                                    }
                                });
                    }
                })
                .onFailure(throwable -> {
                    fireErrorEvent("Unable to register application instance", throwable.getMessage());
                    // We only want to see
                    shouldShowError = false;
                })
                .call(APPLICATION_INSTANCE_RESOURCE)
                .register();
    }

    private void destroy(final String reason) {
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
                        fireErrorEvent("Unable to destroy application instance", throwable.getMessage()))
                .call(APPLICATION_INSTANCE_RESOURCE)
                .destroy(new DestroyRequest(applicationInstanceInfo, reason));
        if (webSocket != null) {
            isOpen = false;
            webSocket.close(reason);
        }
    }

    private void onWebSocketOpen(final OpenEvent event, final String url) {
        Console.log("onWebSocketOpen");
        // do something on open
        isOpen = true;
        errorCount = 0;
        if (!destroy) {
            if (applicationInstanceInfo == null) {
                fireErrorEvent("No application instance is registered");
            } else {
                final WebSocketMessage message = createMessage(applicationInstanceInfo, "Opened web socket");
                sendMessage(webSocket, message);
            }
        }
    }

    private void onWebSocketClose(final CloseEvent event, final String url) {
        Console.log("onWebSocketClose");
        // do something on close
        isOpen = false;
    }

    private void onWebSocketMessage(final MessageEvent event, final String url) {
        Console.log("onWebSocketMessage: " + event.getData());
    }

    private void onWebSocketError(final ErrorEvent event, final String url) {
        Console.log("onWebSocketError");
        errorCount++;

        // Error may be due to the ws connection dying which is semi expected
        // so don't always show
        if (shouldShowError && errorCount > 10) {
            // This method may be called during the opening of the web socket, e.g. if it
            // can't be opened or if there is some problem using the web socket after it is opened.
            if (isOpen) {
                fireErrorEvent("Error on web socket while trying to keep application instance alive",
                        "Error on web socket at " + url + "\n" + event.getReason());
            } else {
                fireErrorEvent("Error opening web socket",
                        "Error opening web socket at " + url + "\n" + event.getReason());
            }
        } else {
            isOpen = false;
            keepAliveWebsocket(0);
        }
    }

    private WebSocketMessage createMessage(final ApplicationInstanceInfo applicationInstanceInfo,
                                           final String info) {
        final WebSocketMessage wsMessage = WebSocketMessage.of(
                ApplicationInstanceResource.WEB_SOCKET_MSG_KEY_UUID,
                applicationInstanceInfo.getUuid(),
                ApplicationInstanceResource.WEB_SOCKET_MSG_KEY_USER_ID,
                applicationInstanceInfo.getUserId(),
                IsWebSocket.WEB_SOCKET_MSG_KEY_INFO,
                info);
        return wsMessage;
    }

    private void keepAliveWebsocket(final int intervalMs) {
        try {
            if (!destroy) {
                WebSocket webSocket = this.webSocket;

                // It is possible closure of the webSocket has cleared out the webSocket variable.
                // If so, just ignore it and it will get re-initiated.
                if (!isOpen) {
                    final String url = WebSocketUtil.createWebSocketUrl("/application-instance");
                    Console.log("Using Web Socket URL: " + url);
                    webSocket = new WebSocket(url, new WebSocketListener() {
                        @Override
                        public void onOpen(final OpenEvent event) {
                            onWebSocketOpen(event, url);
                        }

                        @Override
                        public void onClose(final CloseEvent event) {
                            onWebSocketClose(event, url);
                        }

                        @Override
                        public void onMessage(final MessageEvent event) {
                            onWebSocketMessage(event, url);
                        }

                        @Override
                        public void onError(final ErrorEvent event) {
                            onWebSocketError(event, url);
                        }
                    });
                    this.webSocket = webSocket;

                } else {
                    GWT.log("Sending timed keep alive message (interval: " + intervalMs + "ms)");
                    final WebSocketMessage message = createMessage(
                            applicationInstanceInfo,
                            "Timed keep alive (interval " + intervalMs + "ms)");
                    sendMessage(webSocket, message);
                }
            }
        } catch (final Exception e) {
            Console.log(e.getMessage(), e);
        }
    }

    private void sendMessage(final WebSocket webSocket, final WebSocketMessage message) {
        try {
            webSocket.send(message);
        } catch (Exception e) {
            // The web socket may be closed for various reasons not under our control
            // so, we have to allow for the send call to fail
            Console.log("Error sending keep alive (uuid: "
                    + (applicationInstanceInfo != null
                    ? applicationInstanceInfo.getUuid()
                    : null)
                    + " user: "
                    + (applicationInstanceInfo != null
                    ? applicationInstanceInfo.getUserId()
                    : null)
                    + "). Web socket may have closed. Cause: "
                    + e.getMessage());
        }
    }

    private void fireErrorEvent(final String message) {
        Console.log("Error: " + message);
        if (!showingError) {
            showingError = true;
            AlertEvent.fireError(this,
                    message,
                    () -> showingError = false);
        }
    }

    private void fireErrorEvent(final String message, final String detail) {
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
