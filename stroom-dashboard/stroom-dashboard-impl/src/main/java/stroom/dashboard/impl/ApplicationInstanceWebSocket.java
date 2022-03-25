package stroom.dashboard.impl;

import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.IsWebSocket;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    public static final String PATH = "/application-instance-ws";

    private final SecurityContext securityContext;
    private final ApplicationInstanceManager applicationInstanceManager;
    private volatile ScheduledExecutorService executorService;

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
                + ", message: [" + message + "]");

        // Ensure a user is logged in.
        checkLogin();

        if (executorService == null) {
            final String uuid = message;
            if (refresh(uuid)) {
                executorService = Executors.newScheduledThreadPool(1);
                executorService.scheduleWithFixedDelay(() -> {
                    if (!refresh(uuid)) {
                        executorService.shutdown();
                    }
                }, 1, 1, TimeUnit.SECONDS);
            }
        }
    }

    private boolean refresh(final String uuid) {
        LOGGER.debug(() -> "Refreshing application instance with uuid = " + uuid);
        final boolean ok = applicationInstanceManager.refresh(uuid);
        if (!ok) {
            LOGGER.error(() -> "Unable to refresh application instance with uuid = " + uuid);
        }
        return ok;
    }

    public void onError(final Session session, final Throwable thr) {
        LOGGER.error(thr.getMessage(), thr);
    }

    public synchronized void onClose(final Session session, final CloseReason cr) {
        LOGGER.debug(() -> "Closing web socket at " + PATH
                + ", sessionId: " + session.getId());
        if (executorService != null) {
            executorService.shutdown();
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
