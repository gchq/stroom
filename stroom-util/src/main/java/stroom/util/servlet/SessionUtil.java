/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
                SessionUtil.getSessionId(request),
                hasCookie);

        return hasCookie;
    }

    public static boolean hasSession(final HttpServletRequest request) {
        return request.getSession(false) != null;
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
            HttpSession session = request.getSession(false);
            if (session == null) {
                session = request.getSession(true);
                if (onSessionCreate != null) {
                    // It is possible that two threads could do this but that seems unlikely
                    // for a human to do that.
                    onSessionCreate.accept(session);
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("getOrCreateSession() - Created session: {}, user-agent: {}",
                            session.getId(), UserAgentSessionUtil.getUserAgent(session));
                }
            } else {
                LOGGER.debug("getOrCreateSession() - Existing session: {}", session.getId());
            }
            return session;
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
     * Passes the {@link HttpSession} to sessionConsumer IF there is a session.
     * Does NOT create a new session.
     */
    public static void withSession(final HttpServletRequest request,
                                   final Consumer<HttpSession> sessionConsumer) {
        final HttpSession session = SessionUtil.getExistingSession(request);
        NullSafe.consume(session, sessionConsumer);
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
            return getAttribute(session, name, type);
        } else {
            return null;
        }
    }

    /**
     * Gets an attribute from the session if the session and attribute exists.
     * If createSession is true, a session will be created if it does not exist.
     */
    public static <T> T getAttribute(final HttpSession session,
                                     final String name,
                                     final Class<T> type) {
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
