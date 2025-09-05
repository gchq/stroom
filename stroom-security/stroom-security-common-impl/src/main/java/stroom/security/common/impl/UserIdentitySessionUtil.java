package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Arrays;
import java.util.Optional;
import javax.swing.text.html.Option;

public final class UserIdentitySessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentitySessionUtil.class);

    private static final String SESSION_USER_IDENTITY = "SESSION_USER_IDENTITY";

    private UserIdentitySessionUtil() {
    }

    /**
     * Set the {@link UserIdentity} on the session
     */
    public static void set(final HttpSession session, final UserIdentity userIdentity) {
        LOGGER.debug(() -> LogUtil.message("Setting userIdentity {} of type {} in session {}",
                userIdentity,
                NullSafe.get(userIdentity, UserIdentity::getClass, Class::getSimpleName),
                NullSafe.get(session, HttpSession::getId)));
        session.setAttribute(SESSION_USER_IDENTITY, userIdentity);
    }

    public static Optional<UserIdentity> get(final HttpSession session) {
        return Optional.ofNullable(session)
                .map(session2 -> (UserIdentity) session2.getAttribute(SESSION_USER_IDENTITY));
    }
}
