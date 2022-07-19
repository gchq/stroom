package stroom.dashboard.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.shared.IsWebSocket;

import java.io.IOException;
import java.io.UncheckedIOException;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

public abstract class AuthenticatedWebSocket implements IsWebSocket {

    private final SecurityContext securityContext;
    private final UserIdentity userIdentity;

    @Inject
    public AuthenticatedWebSocket(final SecurityContext securityContext) {
        if (!securityContext.isLoggedIn()) {
            throw new RuntimeException("No authenticated user");
        }

        this.securityContext = securityContext;
        userIdentity = securityContext.getUserIdentity();
    }

    @OnOpen
    public final void onOpenEvent(final Session session) throws IOException {
        wrapIO(() -> onOpen(session));
    }

    @OnMessage
    public final void onMessageEvent(final Session session, final String message) {
        wrap(() -> onMessage(session, message));
    }

    @OnError
    public final void onErrorEvent(final Session session, final Throwable throwable) {
        wrap(() -> onError(session, throwable));
    }

    @OnClose
    public final void onCloseEvent(final Session session, final CloseReason reason) {
        wrap(() -> onClose(session, reason));
    }

    public void onOpen(final Session session) throws IOException {
    }

    public void onMessage(final Session session, final String message) {
    }

    public void onError(final Session session, final Throwable throwable) {
    }

    public void onClose(final Session session, final CloseReason reason) {
    }

    private void wrap(final Runnable runnable) {
        securityContext.asUser(userIdentity, runnable);
    }

    private void wrapIO(final IORunnable runnable) throws IOException {
        try {
            securityContext.asUser(userIdentity, () -> {
                try {
                    runnable.run();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private interface IORunnable {

        void run() throws IOException;
    }
}
