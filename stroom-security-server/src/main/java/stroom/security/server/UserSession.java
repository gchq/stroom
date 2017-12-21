package stroom.security.server;

import stroom.security.shared.UserRef;

import javax.servlet.http.HttpSession;

public class UserSession {
    private static final String USER_REF = "USER_REF";

    private final HttpSession session;

    public UserSession(final HttpSession session) {
        this.session = session;
    }

    public UserRef getUserRef() {
        return (UserRef) session.getAttribute(USER_REF);
    }

    public void setUserRef(final UserRef userRef) {
        session.setAttribute(USER_REF, userRef);
    }
}
