package stroom.security.impl.session;

import stroom.security.api.UserIdentity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Arrays;

public final class UserIdentitySessionUtil {
    private static final String SESSION_USER_IDENTITY = "SESSION_USER_IDENTITY";
    private static final String STROOM_SESSION_ID = "STROOM_SESSION_ID";
    private static final String JSESSIONID = "JSESSIONID";

    private UserIdentitySessionUtil() {
    }

    public static void set(final HttpSession session, final UserIdentity userIdentity) {
        session.setAttribute(SESSION_USER_IDENTITY, userIdentity);
    }

    public static UserIdentity get(final HttpSession session) {
        if (session == null) {
            return null;
        }
        return (UserIdentity) session.getAttribute(SESSION_USER_IDENTITY);
    }

    public static boolean requestHasSessionCookie(final HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return false;
        }

        // Find out if we have a session cookie
        final long count = Arrays
                .stream(request.getCookies())
                .filter(cookie ->
                        cookie.getName().equalsIgnoreCase(STROOM_SESSION_ID) ||
                                cookie.getName().equalsIgnoreCase(JSESSIONID))
                .count();
        return count > 0;
    }
}