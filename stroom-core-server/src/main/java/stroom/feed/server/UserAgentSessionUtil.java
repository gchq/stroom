package stroom.feed.server;

import stroom.feed.StroomHeaderArguments;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class UserAgentSessionUtil {
    private UserAgentSessionUtil() {
        // Utility class.
    }

    public static void set(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            final String userAgent = request.getHeader(StroomHeaderArguments.USER_AGENT);
            if (userAgent != null) {
                session.setAttribute(StroomHeaderArguments.USER_AGENT, userAgent);
            }
        }
    }

    public static String get(final HttpSession session) {
        if (session != null) {
            final Object userAgent = session.getAttribute(StroomHeaderArguments.USER_AGENT);
            if (userAgent != null) {
                return userAgent.toString();
            }
        }
        return null;
    }
}
