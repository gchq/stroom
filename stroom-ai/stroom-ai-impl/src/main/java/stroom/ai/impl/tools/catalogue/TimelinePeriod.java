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

package stroom.ai.impl.tools.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The kind of period a `period_start` or `period_end` role starts or ends, and the key its starts and ends are
/// paired by (decided with the user 2026-11-26). A kind is a fixed identifier, so periods can be searched, counted
/// and compared: one of the well-known kinds, or one named by the user or the agent.
///
/// | Kind | What | Default key |
/// | --- | --- | --- |
/// | `remote_session` | VPN or remote desktop connect to disconnect | `host\|SessionId` |
/// | `on_site` | physical access enter to leave | `Location/Site\|Location/Building` |
/// | `elevated` | privilege gained to dropped | `host, RunAs/Id` |
/// | `device_attached` | storage or a device inserted to removed | `host, Device/Id\|Device/Name\|DeviceId` |
/// | `call` | a call or meeting joined to left | `SessionId` |
/// | any other | as named | `host` |
///
/// A key is parts separated by `,`, each part alternatives separated by `|` (the first with a value used); `host` is
/// the event's host; a path matches an event's key path ending in it, or a Data entry of that name.
///
/// @param kind Its kind, e.g. `remote_session` ([#kindOf]).
/// @param key  The key its starts and ends are paired by, or null for its kind's default ([#defaultKey]).
public record TimelinePeriod(String kind, String key) {

    /// A VPN or remote desktop session.
    public static final String REMOTE_SESSION = "remote_session";
    /// Physically on site.
    public static final String ON_SITE = "on_site";
    /// Holding elevated privilege.
    public static final String ELEVATED = "elevated";
    /// A storage device or other device attached.
    public static final String DEVICE_ATTACHED = "device_attached";
    /// On a call or in a meeting.
    public static final String CALL = "call";
    /// The well-known kinds.
    public static final List<String> KINDS = List.of(REMOTE_SESSION, ON_SITE, ELEVATED, DEVICE_ATTACHED, CALL);
    /// The key part that is the event's host.
    public static final String HOST = "host";
    /// The longest kind.
    public static final int MAX_KIND_CHARS = 40;

    private static final Map<String, String> DEFAULT_KEYS = Map.of(
            REMOTE_SESSION, "host|SessionId",
            ON_SITE, "Location/Site|Location/Building",
            ELEVATED, "host, RunAs/Id",
            DEVICE_ATTACHED, "host, Device/Id|Device/Name|DeviceId",
            CALL, "SessionId");
    private static final Pattern ROLE_TEXT = Pattern.compile(
            "^\\s*(period_start|period_end)(?:\\s+([A-Za-z0-9_ -]+?))?(?:\\s+by\\s+(.+?))?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_KIND = Pattern.compile("[^a-z0-9_]+");

    /// Normalises its kind and key: a kind as [#kindOf] gives it, a blank key none.
    public TimelinePeriod {
        kind = kindOf(kind);
        key = key == null || key.isBlank()
                ? null
                : key.strip();
    }

    /// @param given A kind as given, e.g. `Remote session`, or null.
    /// @return It as a kind: lower case, runs of anything but letters, digits and `_` as one `_`, at most
    /// [#MAX_KIND_CHARS]; null where nothing is left.
    public static String kindOf(final String given) {
        if (given == null) {
            return null;
        }
        String kind = NOT_KIND.matcher(given.strip().toLowerCase(Locale.ROOT)).replaceAll("_");
        kind = kind.replaceAll("^_+|_+$", "");
        if (kind.length() > MAX_KIND_CHARS) {
            kind = kind.substring(0, MAX_KIND_CHARS);
        }
        return kind.isEmpty()
                ? null
                : kind;
    }

    /// @param kind A kind.
    /// @return The key its starts and ends are paired by unless a role gives its own: a well-known kind's, else the
    /// host.
    public static String defaultKey(final String kind) {
        return DEFAULT_KEYS.getOrDefault(kind, HOST);
    }

    /// @return The key it is paired by: its own, else its kind's default.
    public String effectiveKey() {
        return key != null
                ? key
                : defaultKey(kind);
    }

    /// @return True if its kind is one of the well-known kinds.
    public boolean wellKnown() {
        return KINDS.contains(kind);
    }

    /// @return Its kind in words, e.g. `remote session`.
    public String words() {
        return kind == null
                ? "period"
                : kind.replace('_', ' ');
    }

    /// @param role `period_start` or `period_end`.
    /// @return Its text form with that role, e.g. `period_start on_site by Location/Building`.
    public String text(final TimelineRole role) {
        return role.label() + (kind == null
                ? ""
                : " " + kind) + (key == null
                ? ""
                : " by " + key);
    }

    /// @param keyText A key, e.g. `host, Device/Id|Device/Name`.
    /// @return Its parts, each its alternatives, in order.
    public static List<List<String>> parts(final String keyText) {
        final List<List<String>> parts = new ArrayList<>();
        if (keyText == null || keyText.isBlank()) {
            return parts;
        }
        for (final String part : keyText.split(",")) {
            final List<String> alternatives = new ArrayList<>();
            for (final String alternative : part.split("\\|")) {
                if (!alternative.isBlank()) {
                    alternatives.add(alternative.strip());
                }
            }
            if (!alternatives.isEmpty()) {
                parts.add(alternatives);
            }
        }
        return parts;
    }

    /// The values an event's key takes: for each part of the key, the first alternative with a value.
    ///
    /// @param keyText A key ([#parts]).
    /// @param host    The event's host, or null.
    /// @param keys    The event's values by their paths (an Events XML's leaves, an index's fields).
    /// @return Each part's value that has one, in order; empty where none does (the kind alone).
    public static List<String> values(final String keyText, final String host, final Map<String, String> keys) {
        final List<String> values = new ArrayList<>();
        for (final List<String> part : parts(keyText)) {
            for (final String alternative : part) {
                final String value = HOST.equalsIgnoreCase(alternative)
                        ? host
                        : valueAt(alternative, keys);
                if (value != null && !value.isBlank()) {
                    values.add(value.strip());
                    break;
                }
            }
        }
        return values;
    }

    /// @param path A key's path, e.g. `Location/Site` or `SessionId`.
    /// @param keys An event's values by their paths.
    /// @return The value of the first path that is it, ends in it, or is a Data entry of its name
    /// (`SessionId` for `Data[SessionId]`); null if none.
    public static String valueAt(final String path, final Map<String, String> keys) {
        if (keys == null || path == null) {
            return null;
        }
        final String exact = keys.get(path);
        if (exact != null) {
            return exact;
        }
        final String data = "Data[" + path + "]";
        for (final Map.Entry<String, String> entry : keys.entrySet()) {
            final String at = entry.getKey();
            if (at.endsWith("/" + path) || at.equals(data) || at.endsWith("/" + data)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /// @param text A role's text, e.g. `period_start remote_session` or `period_end on_site by Location/Building`.
    /// @return The period role it gives, with its kind and any key; empty for any other text (another role, or a
    /// period role with no kind).
    public static Optional<RoleText> parse(final String text) {
        if (text == null) {
            return Optional.empty();
        }
        final Matcher matcher = ROLE_TEXT.matcher(text);
        if (!matcher.matches() || kindOf(matcher.group(2)) == null) {
            return Optional.empty();
        }
        final TimelineRole role = TimelineRole.of(matcher.group(1)).orElseThrow();
        return Optional.of(new RoleText(role, new TimelinePeriod(matcher.group(2), matcher.group(3))));
    }

    /// @param text A role's text, e.g. `action`, `period_start on_site`.
    /// @return The role it names, a period role with its kind; empty where it names none, or a period role with no
    /// kind.
    public static Optional<RoleText> role(final String text) {
        if (text == null) {
            return Optional.empty();
        }
        final Optional<RoleText> period = parse(text);
        if (period.isPresent()) {
            return period;
        }
        return TimelineRole.of(text)
                .filter(role -> !role.isPeriod())
                .map(role -> new RoleText(role, null));
    }


    // --------------------------------------------------------------------------------


    /// A role as text gives it.
    ///
    /// @param role   The role.
    /// @param period Its period, for a period role; else null.
    public record RoleText(TimelineRole role, TimelinePeriod period) {

        /// @return It as text, e.g. `period_start on_site` or `focus`.
        public String text() {
            return period == null
                    ? role.label()
                    : period.text(role);
        }
    }
}
