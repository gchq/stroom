package stroom.util.servlet;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
}
