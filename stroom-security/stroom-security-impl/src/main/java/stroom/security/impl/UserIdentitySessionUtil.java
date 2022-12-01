package stroom.security.impl;

import stroom.security.api.UserIdentity;

import java.util.Arrays;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class UserIdentitySessionUtil {

    private static final String SESSION_USER_IDENTITY = "SESSION_USER_IDENTITY";
    private static final String STROOM_SESSION_ID = "STROOM_SESSION_ID";
    private static final String JSESSIONID = "JSESSIONID";

    private UserIdentitySessionUtil() {
    }

    public static void set(final HttpSession session, final UserIdentity userIdentity) {
        session.setAttribute(SESSION_USER_IDENTITY, userIdentity);
    }

    public static Optional<UserIdentity> get(final HttpSession session) {
        return Optional.ofNullable(session)
                .map(session2 -> (UserIdentity) session2.getAttribute(SESSION_USER_IDENTITY));
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
