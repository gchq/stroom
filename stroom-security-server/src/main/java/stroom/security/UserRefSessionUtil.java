package stroom.security;

import stroom.security.shared.UserRef;

import javax.servlet.http.HttpSession;

public final class UserRefSessionUtil {
    private static final String SESSION_USER_REF = "SESSION_USER_REF";

    private UserRefSessionUtil() {
    }

    public static void set(final HttpSession session, final UserRef userRef) {
        session.setAttribute(SESSION_USER_REF, userRef);
    }

    public static UserRef get(final HttpSession session) {
        if (session == null) {
            return null;
        }
        return (UserRef) session.getAttribute(SESSION_USER_REF);
    }
}