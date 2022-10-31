package stroom.dashboard.impl;

import stroom.instance.shared.ApplicationInstanceResource;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.IsWebSocket;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.WebSocketMessage;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@Metered
@Timed
@ExceptionMetered
@ServerEndpoint(ApplicationInstanceWebSocket.PATH)
@Singleton
public class ApplicationInstanceWebSocket extends AuthenticatedWebSocket implements IsWebSocket {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApplicationInstanceWebSocket.class);

    public static final String PATH = ResourcePaths.WEB_SOCKET_ROOT_PATH + "/application-instance";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecurityContext securityContext;
    private final ApplicationInstanceManager applicationInstanceManager;
    private final Map<Session, Optional<String>> activeSessions = new ConcurrentHashMap<>();

    @Inject
    public ApplicationInstanceWebSocket(final SecurityContext securityContext,
                                        final ApplicationInstanceManager applicationInstanceManager) {
        super(securityContext);
        this.securityContext = securityContext;
        this.applicationInstanceManager = applicationInstanceManager;

        // Keep any active queries alive that have not aged off from the cache
        Executors.newScheduledThreadPool(1)
                .scheduleWithFixedDelay(
                        this::keepAllActiveQueriesAlive,
                        10,
                        10,
                        TimeUnit.SECONDS);
    }

    private void keepAllActiveQueriesAlive() {
        applicationInstanceManager.evictExpiredElements();
        for (final Entry<Session, Optional<String>> entry : activeSessions.entrySet()) {
            entry.getValue().ifPresent(uuid -> {
                LOGGER.debug(() -> "Keeping application instance alive with uuid = " + uuid);
                applicationInstanceManager.keepAlive(uuid);
                try {
                    LOGGER.debug(() -> "sendText: 'Keep alive: " + uuid + "'");
                    entry.getKey().getAsyncRemote().sendText("Keep alive: " + uuid);
                } catch (final Exception exception) {
                    LOGGER.debug(exception::getMessage, exception);
                }
            });
        }
    }

    public void onOpen(final Session session) throws IOException {
        LOGGER.debug(() ->
                LogUtil.message("Web socket onOpen() called at {}, sessionId: {}, user: {}, maxIdleTimeout: '{}'",
                        PATH, session.getId(), securityContext.getUserId(), session.getMaxIdleTimeout()));

        // Keep alive forever.
        session.setMaxIdleTimeout(0);
        activeSessions.put(session, Optional.empty());

        // You can use this in dev to test handling of unexpected ws closure
//        CompletableFuture.delayedExecutor(new Random().nextInt(5), TimeUnit.SECONDS)
//                .execute(() -> {
//                    try {
//                        LOGGER.info(LogUtil.inSeparatorLine("Closing ws session from server side"));
//                        session.close();
//                    } catch (IOException e) {
//                        throw new RuntimeException(LogUtil.message("Error closing session"), e);
//                    }
//                });
    }

    public void onMessage(final Session session, final String message) {
        LOGGER.debug(() ->
                LogUtil.message("Web socket onMessage() called at {}, sessionId: {}, user: {}, message: '{}'",
                        PATH, session.getId(), securityContext.getUserId(), message));

        // Ensure a user is logged in.
        checkLogin();

        if (message != null && !message.isEmpty()) {
            try {
                // De-ser the web socket msg
                final WebSocketMessage webSocketMessage = OBJECT_MAPPER.readValue(message, WebSocketMessage.class);

                final String key = ApplicationInstanceResource.WEB_SOCKET_MSG_KEY_UUID;
                // May just be an informational message
                getWebSocketMsgValue(webSocketMessage, key, String.class)
                        .ifPresent(uuid -> {
                            activeSessions.put(session, Optional.of(uuid));
                            applicationInstanceManager.keepAlive(uuid);
                        });
            } catch (IOException e) {
                throw new RuntimeException(LogUtil.message(
                        "Unable to de-serialise web socket message: '{}'. Cause: {}",
                        message, e.getMessage()), e);
            }
        }
    }

    public void onError(final Session session, final Throwable throwable) {
        LOGGER.error(LogUtil.message("Web socket onError() called at {}, sessionId: {}, user: {}, message: {}",
                PATH, session.getId(), securityContext.getUserId(), throwable.getMessage()), throwable);
    }

    public void onClose(final Session session, final CloseReason closeReason) {
        activeSessions.remove(session);
        LOGGER.debug(() ->
                LogUtil.message("Web socket onClose() called at {}, sessionId: {}, user: {}, closeReason: {}",
                        PATH, session.getId(), securityContext.getUserId(), closeReason));
    }

    private void checkLogin() {
        try {
            if (!securityContext.isLoggedIn()) {
                throw new EntityServiceException("No user is logged in");
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }
}
