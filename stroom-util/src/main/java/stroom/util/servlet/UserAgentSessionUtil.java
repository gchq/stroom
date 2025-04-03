package stroom.util.servlet;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class UserAgentSessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserAgentSessionUtil.class);

    private static final String USER_AGENT = "user-agent";

    private UserAgentSessionUtil() {
        // Utility class.
    }

    public static void set(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            final String userAgent = request.getHeader(USER_AGENT);
            if (userAgent != null) {
                LOGGER.debug(() -> LogUtil.message("Setting {} '{}' in session {}",
                        USER_AGENT,
                        userAgent,
                        NullSafe.get(session, HttpSession::getId)));
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
