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
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@Metered
@Timed
@ExceptionMetered
@ServerEndpoint(ApplicationInstanceWebSocket.PATH)
public class ApplicationInstanceWebSocket extends AuthenticatedWebSocket implements IsWebSocket {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApplicationInstanceWebSocket.class);

    public static final String PATH = ResourcePaths.WEB_SOCKET_ROOT_PATH + "/application-instance";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecurityContext securityContext;
    private final ApplicationInstanceManager applicationInstanceManager;

    @Inject
    public ApplicationInstanceWebSocket(final SecurityContext securityContext,
                                        final ApplicationInstanceManager applicationInstanceManager) {
        super(securityContext);
        this.securityContext = securityContext;
        this.applicationInstanceManager = applicationInstanceManager;
    }

    public void onOpen(final Session session) throws IOException {
        LOGGER.debug(() -> "Opening web socket at " + PATH
                + ", sessionId: " + session.getId()
                + ", maxIdleTimeout: " + session.getMaxIdleTimeout());

        // Keep alive forever.
        session.setMaxIdleTimeout(0);
    }

    public synchronized void onMessage(final Session session, final String message) {
        LOGGER.debug(() -> "Received message on web socket at " + PATH
                + ", sessionId: " + session.getId()
                + ", message: '" + message + "'");
        // Ensure a user is logged in.
        checkLogin();

        if (message != null && !message.isEmpty()) {
            try {
                // De-ser the web socket msg
                final WebSocketMessage webSocketMessage = OBJECT_MAPPER.readValue(message, WebSocketMessage.class);

                final String key = ApplicationInstanceResource.WEB_SOCKET_MSG_KEY_UUID;
                // May just be an informational message
                getWebSocketMsgValue(webSocketMessage, key, String.class)
                                .ifPresent(this::keepAlive);
            } catch (IOException e) {
                throw new RuntimeException(LogUtil.message(
                        "Unable to de-serialise web socket message: '{}'. Cause: {}",
                        message, e.getMessage()), e);
            }
        }
    }

    private void keepAlive(final String uuid) {
        LOGGER.debug(() -> "Keeping application instance alive with uuid = " + uuid);
        applicationInstanceManager.keepAlive(uuid);
    }

    public void onError(final Session session, final Throwable thr) {
        LOGGER.error(thr.getMessage(), thr);
    }

    public synchronized void onClose(final Session session, final CloseReason cr) {
        LOGGER.debug(() -> "Closing web socket at " + PATH
                + ", sessionId: " + session.getId());
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
