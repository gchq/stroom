package stroom.servlet;

import javax.servlet.http.HttpSession;

public final class HttpSessionUtil {
    private static final String SESSION_USER_API_KEY = "SESSION_USER_API_KEY";

    private HttpSessionUtil() {
    }

    public static void setUserApiKey(final HttpSession session, final String usersApiKey) {
        session.setAttribute(SESSION_USER_API_KEY, usersApiKey);
    }

    public static String getUserApiKey(final HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(SESSION_USER_API_KEY);
    }
}