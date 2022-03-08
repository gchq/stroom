package stroom.dashboard.impl;

import stroom.query.api.v2.QueryKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@Metered
@Timed
@ExceptionMetered
@ServerEndpoint("/active-queries-ws")
public class ActiveQueriesWebSocket {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveQueriesWebSocket.class);

    private static ActiveQueriesManager activeQueriesManager;

    private final Map<String, Set<String>> activeQueries = new ConcurrentHashMap<>();

    public ActiveQueriesWebSocket() {
        Executors.newScheduledThreadPool(1).schedule(() -> {
            if (activeQueriesManager != null) {
                activeQueries.values().forEach(set -> {
                    set.forEach(uuid -> {
                        final Optional<ActiveQuery> optionalActiveQuery =
                                activeQueriesManager.getOptional(new QueryKey(uuid));
                        optionalActiveQuery.ifPresent(ActiveQuery::keepAlive);
                    });
                });
            }
        }, 1, TimeUnit.MINUTES);
    }

    @OnOpen
    public void onOpen(final Session session) throws IOException {
        LOGGER.debug(() -> "onOpen " + session.getMaxIdleTimeout());
        // Keep alive forever.
        session.setMaxIdleTimeout(0);
        session.getAsyncRemote().sendText("welcome");
    }

    @OnMessage
    public void onMessage(final Session session, final String message) {
        LOGGER.debug("onMessage");
        session.getAsyncRemote().sendText(message.toUpperCase());

        if (message.startsWith("add:")) {
            if (activeQueriesManager != null) {
                final String uuid = message.substring("add:".length());
                activeQueries.computeIfAbsent(session.getId(), k ->
                        Collections.newSetFromMap(new ConcurrentHashMap<>())).add(uuid);
//                activeQueriesManager.remove(new QueryKey(uuid));
            }
        } else if (message.startsWith("remove:")) {
            if (activeQueriesManager != null) {
                final String uuid = message.substring("remove:".length());
                activeQueriesManager.remove(new QueryKey(uuid));
                activeQueries.computeIfAbsent(session.getId(), k ->
                        Collections.newSetFromMap(new ConcurrentHashMap<>())).remove(uuid);
            }
        }
    }

    @OnClose
    public void onClose(final Session session, final CloseReason cr) {
        LOGGER.debug("onClose");
        final Set<String> set = activeQueries.remove(session.getId());
        if (set != null && activeQueriesManager != null) {
            set.forEach(uuid -> activeQueriesManager.remove(new QueryKey(uuid)));
        }
    }

    public static void setActiveQueriesManager(final ActiveQueriesManager aqm) {
        activeQueriesManager = aqm;
    }
}
