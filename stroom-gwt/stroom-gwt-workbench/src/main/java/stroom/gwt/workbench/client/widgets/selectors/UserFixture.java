/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.util.shared.UserRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// A fake of Stroom's user search (`POST /userRef/v1/find`), the equivalent of the React stories'
/// `fixtureLoadUsers`: a case-insensitive contains match of the quick filter's text on the users'
/// display names and subject ids.
///
/// Plain Java, so it is unit tested on the JVM.
public final class UserFixture {

    /// The user search's path, relative to the REST root.
    public static final String FIND = "/userRef/v1/find";

    private final List<UserRef> users;

    /// @param users The users and groups.
    public UserFixture(final List<UserRef> users) {
        this.users = users;
    }

    /// The React stories' `FIXTURE_USERS`.
    ///
    /// @return The fixture.
    public static UserFixture fixtureUsers() {
        return new UserFixture(List.of(
                user("u-alice", "alice", "Alice Anderson", "Alice J. Anderson", false, true),
                user("u-bob", "bob", "Bob Brown", "Robert Brown", false, true),
                user("u-carol", "carol", "Carol Clark", "Carol Clark", false, false),
                user("g-admins", "Administrators", "Administrators", null, true, true),
                user("g-analysts", "Analysts", "Analysts", null, true, true)));
    }

    /// Creates a user or group.
    ///
    /// @param uuid        The uuid.
    /// @param subjectId   The subject id.
    /// @param displayName The display name.
    /// @param fullName    The full name, null for a group.
    /// @param group       True for a group.
    /// @param enabled     Whether the user is enabled.
    /// @return The user.
    public static UserRef user(final String uuid,
                               final String subjectId,
                               final String displayName,
                               final String fullName,
                               final boolean group,
                               final boolean enabled) {
        return new UserRef(uuid, subjectId, displayName, fullName, group, enabled);
    }

    /// @param uuid A user's uuid.
    /// @return The user.
    /// @throws IllegalArgumentException If there is no such user.
    public UserRef get(final String uuid) {
        for (final UserRef user : users) {
            if (user.getUuid().equals(uuid)) {
                return user;
            }
        }
        throw new IllegalArgumentException("No user " + uuid);
    }

    /// Answers a `find` request.
    ///
    /// @param requestJson The request's body, a `FindUserCriteria`.
    /// @return The reply's body, a `ResultPage` of the matching users.
    public String find(final String requestJson) {
        final String filter = filterText(JsonValues.parse(requestJson));
        final List<UserRef> matches = new ArrayList<>();
        for (final UserRef user : users) {
            final String text = (nonNull(user.getDisplayName()) + " " + nonNull(user.getSubjectId()))
                    .toLowerCase(Locale.ROOT);
            if (filter == null || text.contains(filter)) {
                matches.add(user);
            }
        }
        final StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            final UserRef user = matches.get(i);
            sb.append("{\"uuid\":").append(quote(user.getUuid()))
                    .append(",\"subjectId\":").append(quote(user.getSubjectId()))
                    .append(",\"displayName\":").append(quote(user.getDisplayName()))
                    .append(",\"fullName\":").append(quote(user.getFullName()))
                    .append(",\"group\":").append(user.isGroup())
                    .append(",\"enabled\":").append(user.isEnabled())
                    .append('}');
        }
        sb.append("],\"pageResponse\":{\"offset\":0,\"length\":").append(matches.size())
                .append(",\"total\":").append(matches.size())
                .append(",\"exact\":true}}");
        return sb.toString();
    }

    /// The quick filter's text: the value of the first term of the request's expression, which
    /// Stroom's `QuickFilterExpressionParser` builds from it.
    private static String filterText(final Object json) {
        if (json instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) json;
            final Object value = map.get("value");
            if (map.containsKey("field") && value != null && !value.toString().trim().isEmpty()) {
                return value.toString().trim().toLowerCase(Locale.ROOT);
            }
            for (final Object child : map.values()) {
                final String text = filterText(child);
                if (text != null) {
                    return text;
                }
            }
        } else if (json instanceof List) {
            for (final Object child : (List<?>) json) {
                final String text = filterText(child);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String nonNull(final String text) {
        return text == null
                ? ""
                : text;
    }

    private static String quote(final String text) {
        if (text == null) {
            return "null";
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
