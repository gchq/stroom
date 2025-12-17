/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.servlet;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class UserAgentSessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserAgentSessionUtil.class);

    private static final String USER_AGENT_HEADER_KEY = "user-agent";

    private UserAgentSessionUtil() {
        // Utility class.
    }

//    public static void setUserAgentInSession(final HttpServletRequest request) {
//        final HttpSession session = request.getSession(false);
//        setUserAgentInSession(request, session);
//    }

    /**
     * If there is a session, it sets the value of the {@code user-agent} header (if present)
     * into the session attribute.
     */
    public static void setUserAgentInSession(final HttpServletRequest request) {
        SessionUtil.withSession(request, session ->
                setUserAgentInSession(request, session));
    }

    /**
     * Sets value of the {@code user-agent} header into a session attribute, IF there is
     * a session.
     */
    public static void setUserAgentInSession(final HttpServletRequest request,
                                             final HttpSession session) {
        if (session != null) {
            final String userAgent = request.getHeader(USER_AGENT_HEADER_KEY);
            if (userAgent != null) {
                LOGGER.debug(() -> LogUtil.message("setUserAgentInSession() - Setting {} '{}' in session {}",
                        USER_AGENT_HEADER_KEY,
                        userAgent,
                        NullSafe.get(session, HttpSession::getId)));
                session.setAttribute(USER_AGENT_HEADER_KEY, userAgent);
            }
        } else {
            LOGGER.debug("setUserAgentInSession() - No session");
        }
    }

    public static String getUserAgent(final HttpSession session) {
        return SessionUtil.getAttribute(session, USER_AGENT_HEADER_KEY, String.class);
    }
}
