package stroom.core.dispatch;

import com.google.common.hash.Hashing;
import com.google.gwt.user.client.rpc.RpcTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;

final class SessionHashUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionHashUtil.class);

    private SessionHashUtil() {
        // Utility class.
    }

    static String createSessionHash(final HttpServletRequest request) {
        try {
            LOGGER.debug("Creating session hash");

            final String sessionId = getSessionId(request);
            if (sessionId == null || sessionId.length() == 0) {
                throw new RpcTokenException("Session id not set or empty! Unable to create session hash");
            }

            // Hash the session cookie value.
            return Hashing.sha512().hashString(sessionId, StandardCharsets.UTF_8).toString();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private static String getSessionId(final HttpServletRequest request) {
        if (request != null) {
            final HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                return httpSession.getId();
            }
        }

        return null;
    }
}
