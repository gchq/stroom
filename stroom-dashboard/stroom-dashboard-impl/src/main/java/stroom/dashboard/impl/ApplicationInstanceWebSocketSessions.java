package stroom.dashboard.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.websocket.Session;

@Singleton
public class ApplicationInstanceWebSocketSessions {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(ApplicationInstanceWebSocketSessions.class);

    private final Map<Session, Optional<String>> activeSessions = new ConcurrentHashMap<>();
    private final ApplicationInstanceManager applicationInstanceManager;

    @Inject
    public ApplicationInstanceWebSocketSessions(final ApplicationInstanceManager applicationInstanceManager) {
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

    public void putSession(final Session session, final Optional<String> applicationInstanceId) {
        activeSessions.put(session, applicationInstanceId);
        // Keep alive application instances if we have a UUID
        applicationInstanceId.ifPresent(applicationInstanceManager::keepAlive);
    }

    public void removeSession(final Session session) {
        activeSessions.remove(session);
    }
}
