package stroom.util.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class UserAgentSessionUtil {
    private static final String USER_AGENT = "user-agent";

    private UserAgentSessionUtil() {
        // Utility class.
    }

    public static void set(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            final String userAgent = request.getHeader(USER_AGENT);
            if (userAgent != null) {
                session.setAttribute(USER_AGENT, userAgent);
            }
        }
    }

    public static String get(final HttpSession session) {
        if (session != null) {
            final Object userAgent = session.getAttribute(USER_AGENT);
            if (userAgent != null) {
                return userAgent.toString();
            }
        }
        return null;
    }
}
