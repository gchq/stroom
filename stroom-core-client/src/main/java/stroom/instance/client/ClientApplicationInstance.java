package stroom.instance.client;

import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.instance.shared.ApplicationInstanceInfo;
import stroom.instance.shared.ApplicationInstanceResource;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.LogoutEvent;
import stroom.util.client.Console;
import stroom.websocket.client.CloseEvent;
import stroom.websocket.client.ErrorEvent;
import stroom.websocket.client.MessageEvent;
import stroom.websocket.client.OpenEvent;
import stroom.websocket.client.WebSocket;
import stroom.websocket.client.WebSocketListener;
import stroom.websocket.client.WebSocketUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientApplicationInstance {

    private static final ApplicationInstanceResource APPLICATION_INSTANCE_RESOURCE =
            GWT.create(ApplicationInstanceResource.class);

    private final RestFactory restFactory;
    private final ClientError clientError;

    private ApplicationInstanceInfo applicationInstanceInfo;
    private WebSocket webSocket;
    private boolean destroy;

    @Inject
    public ClientApplicationInstance(final RestFactory restFactory,
                                     final EventBus eventBus,
                                     final ClientError clientError) {
        this.restFactory = restFactory;
        this.clientError = clientError;

        eventBus.addHandler(CurrentUserChangedEvent.getType(), event -> register());
        eventBus.addHandler(LogoutEvent.getType(), event -> destroy());
    }

    public String getInstanceUuid() {
        if (applicationInstanceInfo == null) {
            clientError.error("Null application instance uuid");
            return null;
        }
        return applicationInstanceInfo.getUuid();
    }

    public void register() {
        final Rest<ApplicationInstanceInfo> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    applicationInstanceInfo = result;
                    // Start long poll refresh loop.
                    refresh();
                    // Also try using a web socket to keep the instance alive.
                    tryWebSocket();
                })
                .onFailure(throwable ->
                        clientError.error("Unable to register application instance", throwable.getMessage()))
                .call(APPLICATION_INSTANCE_RESOURCE)
                .register();
    }

    private void refresh() {
        final Rest<Boolean> rest = restFactory.createQuiet();
        rest
                .onSuccess(result -> {
                    if (result) {
                        Scheduler.get().scheduleDeferred(this::refresh);
                    } else {
                        clientError.error("Unable to keep application instance alive");
                    }
                })
                .onFailure(throwable ->
                        clientError.error("Unable to keep application instance alive", throwable.getMessage()))
                .call(APPLICATION_INSTANCE_RESOURCE)
                .refresh(applicationInstanceInfo);
    }

    private void destroy() {
        destroy = true;
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    if (result) {
                        Scheduler.get().scheduleDeferred(this::refresh);
                    } else {
                        clientError.error("Unable to destroy application instance");
                    }
                })
                .onFailure(throwable ->
                        clientError.error("Unable to destroy application instance", throwable.getMessage()))
                .call(APPLICATION_INSTANCE_RESOURCE)
                .destroy(applicationInstanceInfo);
        webSocket.close();
    }

    private void tryWebSocket() {
        if (!destroy) {
            final String url = WebSocketUtil.createWebSocketUrl("/application-instance-ws");
            Console.log("Using Web Socket URL: " + url);
            webSocket = new WebSocket(url, new WebSocketListener() {
                @Override
                public void onOpen(final OpenEvent event) {
                    // do something on open
                    Console.log("Opening web socket at " + url);
                    if (!destroy && webSocket != null) {
                        if (applicationInstanceInfo == null) {
                            clientError.error("No application instance is registered");
                        } else {
                            webSocket.send(applicationInstanceInfo.getUuid());
                        }
                    }
                }

                @Override
                public void onClose(final CloseEvent event) {
                    // do something on close
                    Console.log("Closing web socket at " + url);

                    // Try to reopen the web socket if closing it was unexpected.
                    tryWebSocket();
                }

                @Override
                public void onMessage(final MessageEvent event) {
                }

                @Override
                public void onError(final ErrorEvent event) {
                    Console.log("Error on web socket at " + url);
                    clientError.error("Error on web socket at " + url, event.getReason());
                }
            });
        }
    }


}
