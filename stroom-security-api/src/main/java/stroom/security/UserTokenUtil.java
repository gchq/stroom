/*
 * Copyright 2016 Crown Copyright
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

package stroom.security;

public final class UserTokenUtil {
    public static final String INTERNAL_PROCESSING_USER_TOKEN = createInternal();

    private static final String DELIMITER = "|";
    private static final String INTERNAL = "INTERNAL";
    private static final String USER = "user";
    private static final String SYSTEM = "system";

    private UserTokenUtil() {
        // Utility class.
    }

    private static String createInternal() {
        return create(SYSTEM, INTERNAL, null);
    }

    public static String create(final String userId, final String sessionId) {
        return create(USER, userId, sessionId);
    }

    private static String create(final String type, final String userId, final String sessionId) {
        final StringBuilder sb = new StringBuilder();
        if (type != null) {
            sb.append(type);
        }
        sb.append(DELIMITER);
        if (userId != null) {
            sb.append(userId);
        }
        sb.append(DELIMITER);
        if (sessionId != null) {
            sb.append(sessionId);
        }
        return sb.toString();
    }

    public static String getType(final String token) {
        return getPart(token, 0);
    }

    public static String getUserId(final String token) {
        return getPart(token, 1);
    }

    public static String getSessionId(final String token) {
        return getPart(token, 2);
    }

    private static String getPart(final String token, final int index) {
        if (token == null) {
            return null;
        }
        final String[] parts = token.split("\\|", -1);
        if (parts.length - 1 < index) {
            return null;
        }
        return parts[index];
    }
}
