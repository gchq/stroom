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

package stroom.ai.impl.tools.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The event-logging element an input field's name says it belongs in (a client address, a user, a host, a session
/// id). The draft ([stroom.ai.impl.tools.draft.DraftMapping]) maps a field to its home; the translation generator
/// warns of a Data entry whose name has a home the event doesn't map.
public final class FieldHomes {

    /// Each home, with the pattern (matched ignoring case) of the field names that belong there, in the order they
    /// are tried.
    public static final List<Home> HOMES = List.of(
            home("EventSource/Device/HostName", "^(host|hostname|host_?name|devname|device|device_?name"
                                                + "|device_?id|computer|server|node|sensor|appliance|probe|dvchost"
                                                + "|dvc_?host|switch|switch_?name)$"),
            // The device where the event happened, named rather than addressed: a door controller's door or reader
            // (seen: eval case 28, where the agent took the sender's address from stroom:meta instead).
            home("EventSource/Device/Name", "^(door|door_?name|reader|reader_?name)$"),
            home("EventSource/Device/MACAddress", "^(mac|mac_?address|src_?mac|client_?mac)$"),
            home("EventSource/Client/IPAddress", "^(ip|src_?ip|source_?ip|srcaddr|src_?addr|client_?ip"
                                                 + "|client_?addr(ess)?|remote_?addr|remote_?ip|source_?address"
                                                 + "|ip_?address|ipaddress|clientip|src|source)$"),
            home("EventSource/Client/Port", "^(src_?port|source_?port|client_?port|sport|spt)$"),
            home("EventSource/Server/IPAddress", "^(dst_?ip|dest_?ip|destination_?ip|dstaddr|dst_?addr|server_?ip"
                                                 + "|server_?addr(ess)?|target_?ip|destination_?address|dst|dest"
                                                 + "|destination)$"),
            home("EventSource/Server/Port", "^(dst_?port|dest_?port|destination_?port|server_?port|dport"
                                            + "|service_?port|dpt)$"),
            home("EventSource/User/Id", "^(user|username|user_?name|user_?id|userid|account|account_?name|login"
                                        + "|subject|actor|principal|uid|target_?user_?name|suser|src_?user"
                                        + "|source_?user|holder|card_?holder|badge_?holder|badge_?user)$"),
            home("EventSource/RunAs/Id", "^(run_?as|run_?as_?user|effective_?user|euser|as_?user)$"),
            home("EventSource/SessionId", "^(session_?id|sessionid|session_?key)$"),
            home("EventSource/Generator", "^(generator|app|app_?name|application|program|process_?name|logger"
                                          + "|service|cef_?product)$"),
            home("EventDetail/Description", "^(message|msg|description|desc|text|summary|details)$"));

    private static final Pattern DATA_NAME = Pattern.compile("\\[@Name='([^']+)'\\]$");
    private static final Pattern PART_SEPARATOR = Pattern.compile("[./]");

    private FieldHomes() {
        // Static only.
    }

    private static Home home(final String path, final String pattern) {
        return new Home(path, pattern, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    /// The names a field may be known by: its own (the last part of a JSON key or XML path, without an attribute's
    /// `@`), the last two parts joined (`user.name` is `user_name`), and an XML Data element's Name.
    ///
    /// @param name A field name.
    /// @return The names, lower case.
    public static List<String> keys(final String name) {
        final Matcher named = DATA_NAME.matcher(name);
        if (named.find()) {
            return List.of(named.group(1).toLowerCase(Locale.ROOT));
        }
        final List<String> parts = new ArrayList<>();
        for (final String part : PART_SEPARATOR.split(name, -1)) {
            if (!part.isEmpty()) {
                parts.add(lstripAt(part).toLowerCase(Locale.ROOT));
            }
        }
        final List<String> out = new ArrayList<>();
        if (!parts.isEmpty()) {
            out.add(parts.getLast());
        }
        if (parts.size() > 1) {
            out.add(parts.get(parts.size() - 2) + "_" + parts.getLast());
        }
        return out;
    }

    /// @param regex A home's pattern.
    /// @param name  A field name.
    /// @return True if one of the names the field may be known by matches it (at its start).
    public static boolean matches(final Pattern regex, final String name) {
        return keys(name).stream().anyMatch(key -> regex.matcher(key).lookingAt());
    }

    private static String lstripAt(final String part) {
        int start = 0;
        while (start < part.length() && part.charAt(start) == '@') {
            start++;
        }
        return part.substring(start);
    }


    // --------------------------------------------------------------------------------


    /// An element a field may belong in.
    ///
    /// @param path    The path below Event.
    /// @param pattern The pattern of the field names that belong there, as written.
    /// @param regex   The pattern, compiled ignoring case.
    public record Home(String path, String pattern, Pattern regex) {

    }
}
