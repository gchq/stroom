package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;

public final class UserIdentitySessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentitySessionUtil.class);

    private static final String SESSION_USER_IDENTITY = "SESSION_USER_IDENTITY";

    private UserIdentitySessionUtil() {
    }

    /**
     * Set the {@link UserIdentity} on the session
     */
    public static void setUserInSession(final HttpSession session, final UserIdentity userIdentity) {
        Objects.requireNonNull(session);
        LOGGER.debug(() -> LogUtil.message("Setting userIdentity of type {} in session {}, userIdentity: {}",
                LogUtil.getSimpleClassName(userIdentity),
                NullSafe.get(session, HttpSession::getId),
                userIdentity));
        session.setAttribute(SESSION_USER_IDENTITY, userIdentity);
    }

    public static Optional<UserIdentity> getUserFromSession(final HttpSession session) {
        final Optional<UserIdentity> optUserIdentity = Optional.ofNullable(session)
                .map(session2 ->
                        (UserIdentity) session2.getAttribute(SESSION_USER_IDENTITY));

        if (LOGGER.isTraceEnabled()) {
            optUserIdentity.ifPresentOrElse(userIdentity -> {
                LOGGER.trace(() -> LogUtil.message("Got userIdentity of type {} in session {}, userIdentity: {}",
                        NullSafe.get(userIdentity, UserIdentity::getClass, Class::getSimpleName),
                        SessionUtil.getSessionId(session),
                        userIdentity));
            }, () ->
                    LOGGER.trace(() ->
                            LogUtil.message("No userIdentity in session {}", SessionUtil.getSessionId(session))));
        }
        return optUserIdentity;
    }
}
