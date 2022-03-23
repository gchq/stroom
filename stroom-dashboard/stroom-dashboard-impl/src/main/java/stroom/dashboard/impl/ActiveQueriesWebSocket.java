package stroom.dashboard.impl;

import stroom.query.api.v2.QueryKey;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.IsWebSocket;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import java.io.IOException;
import java.util.UUID;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@Metered
@Timed
@ExceptionMetered
@ServerEndpoint(ActiveQueriesWebSocket.PATH)
public class ActiveQueriesWebSocket extends AuthenticatedWebSocket implements IsWebSocket {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveQueriesWebSocket.class);

    public static final String PATH = "/active-queries-ws";

    private final SecurityContext securityContext;
    private final ActiveQueriesManager activeQueriesManager;
    private QueryKey queryKey;

    @Inject
    public ActiveQueriesWebSocket(final SecurityContext securityContext,
                                  final ActiveQueriesManager activeQueriesManager) {
        super(securityContext);
        this.securityContext = securityContext;
        this.activeQueriesManager = activeQueriesManager;
    }

    public void onOpen(final Session session) throws IOException {
        LOGGER.debug(() -> "Opening web socket at " + PATH
                + ", sessionId: " + session.getId()
                + ", maxIdleTimeout: " + session.getMaxIdleTimeout());

        // Ensure a user is logged in.
        checkLogin();

        // Keep alive forever.
        session.setMaxIdleTimeout(0);

        // Create a new search UUID.
        final String uuid = UUID.randomUUID().toString();
        queryKey = new QueryKey(uuid);
        activeQueriesManager.put(queryKey, new ActiveQuery(queryKey, securityContext.getUserId()));
        session.getAsyncRemote().sendText(uuid);
    }

    public void onMessage(final Session session, final String message) {
        LOGGER.debug(() -> "Received message on web socket at " + PATH
                + ", sessionId: " + session.getId()
                + ", message: [" + message + "]");

        // Ensure a user is logged in.
        checkLogin();

        session.getAsyncRemote().sendText(message.toUpperCase());
    }

    public void onError(final Session session, final Throwable thr) {
        LOGGER.error(thr.getMessage(), thr);
    }

    public void onClose(final Session session, final CloseReason cr) {
        LOGGER.debug(() -> "Closing web socket at " + PATH
                + ", sessionId: " + session.getId());
        if (queryKey != null) {
            activeQueriesManager.destroy(queryKey);
        } else {
            LOGGER.error("UUID is null");
        }
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
