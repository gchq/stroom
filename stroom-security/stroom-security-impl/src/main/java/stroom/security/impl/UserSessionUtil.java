package stroom.security.impl;

import stroom.security.shared.User;

import javax.servlet.http.HttpSession;

public final class UserSessionUtil {
    private static final String SESSION_USER_REF = "SESSION_USER_REF";

    private UserSessionUtil() {
    }

    public static void set(final HttpSession session, final User userRef) {
        session.setAttribute(SESSION_USER_REF, userRef);
    }

    public static User get(final HttpSession session) {
        if (session == null) {
            return null;
        }
        return (User) session.getAttribute(SESSION_USER_REF);
    }
}