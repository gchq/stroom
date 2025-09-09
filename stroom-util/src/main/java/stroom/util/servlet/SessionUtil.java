package stroom.util.servlet;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.function.Consumer;

public class SessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SessionUtil.class);

    public static final String STROOM_SESSION_COOKIE_NAME = "STROOM_SESSION_ID";
    public static final String JSESSIONID = "JSESSIONID";

    private SessionUtil() {
    }

    public static boolean requestHasSessionCookie(final HttpServletRequest request) {
        // Find out if we have a session cookie
        final boolean hasCookie = NullSafe.stream(NullSafe.get(request, HttpServletRequest::getCookies))
                .anyMatch(cookie ->
                        cookie.getName().equalsIgnoreCase(STROOM_SESSION_COOKIE_NAME) ||
                        cookie.getName().equalsIgnoreCase(JSESSIONID));

        LOGGER.debug("requestHasSessionCookie() - sessionId: {}, hasCookie: {}",
                NullSafe.get(request, HttpServletRequest::getSession, HttpSession::getId),
                hasCookie);

        return hasCookie;
    }

    /**
     * Gets the existing {@link HttpSession} or returns null if no session is present.
     * Does NOT create a session.
     */
    public static HttpSession getExistingSession(final HttpServletRequest request) {
        if (request != null) {
            final HttpSession session = request.getSession(false);
            LOGGER.trace(() -> LogUtil.message("getExistingSession() - session: {}",
                    getSessionId(request)));
            return session;
        } else {
            return null;
        }
    }

    /**
     * Gets the existing {@link HttpSession} or creates one if there is no existing one.
     *
     * @param onSessionCreate Called if a session didn't previously exist. The session passed to it
     *                        will be non-null.
     */
    public static HttpSession getOrCreateSession(final HttpServletRequest request,
                                                 final Consumer<HttpSession> onSessionCreate) {
        if (request != null) {
            if (onSessionCreate != null) {
                final HttpSession existingSession = request.getSession(false);
                final HttpSession session = request.getSession(true);

                if (existingSession == null && session != null) {
                    onSessionCreate.accept(session);
                }
                return session;
            } else {
                return request.getSession(true);
            }
        } else {
            return null;
        }
    }

    /**
     * Null safe method to get the session ID if there is a session. Does not create a session.
     *
     * @return The session ID or null if there is no session.
     */
    public static String getSessionId(final HttpServletRequest request) {
        return NullSafe.get(
                request,
                req -> req.getSession(false),
                HttpSession::getId);
    }

    /**
     * Null safe method to get the session ID if there is a session. Does not create a session.
     *
     * @return The session ID or null if there is no session.
     */
    public static String getSessionId(final HttpSession session) {
        return NullSafe.get(session, HttpSession::getId);
    }

    /**
     * Gets an attribute from the session if the session and attribute exists.
     * If createSession is true, a session will be created if it does not exist.
     */
    public static <T> T getAttribute(final HttpServletRequest request,
                                     final String name,
                                     final Class<T> type,
                                     final boolean createSession) {
        if (request != null) {
            final HttpSession session = request.getSession(createSession);
            if (session != null) {
                final Object object = session.getAttribute(name);
                LOGGER.debug("getAttribute() - object: {}", object);
                if (object != null) {
                    return type.cast(object);
                } else {
                    return null;
                }
            } else {
                LOGGER.debug("No Session");
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Sets an attribute in the session if the session exists or createSession is true.
     */
    public static void setAttribute(final HttpServletRequest request,
                                    final String name,
                                    final Object value,
                                    final boolean createSession) {
        if (request != null) {
            final HttpSession session = request.getSession(createSession);
            if (session != null) {
                session.setAttribute(name, value);
            } else {
                LOGGER.debug("No Session");
            }
        }
    }
}
