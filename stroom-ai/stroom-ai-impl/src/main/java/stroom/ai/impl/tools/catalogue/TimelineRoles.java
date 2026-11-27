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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The timeline roles of a catalogue's event types (decided with the user 2026-11-25): where an event type's
/// EventDetail element and Action settle it, by the event-logging schema's table, its role is filled in (and filled
/// in again whenever its element or Action changes); the user is asked only for the rest (an `Unknown` or missing
/// element, an element this Stroom's schema fork adds, an `Authenticate` or `Process` Action the table doesn't
/// name), and for any that marks a window gaining focus, which no element of the schema says. Only the schema's own
/// names are known here; nothing of any source.
///
/// | Element and Action | Role |
/// | --- | --- |
/// | Authenticate Logon, ScreenUnlock | `active` |
/// | Authenticate Logoff, ScreenLock | `idle` |
/// | Authenticate Connect, Reconnect | `period_start` of a `remote_session` (decided 2026-11-26) |
/// | Authenticate Disconnect | `period_end` of a `remote_session` |
/// | Process Execute, Startup | `open` |
/// | Process Terminate, Shutdown | `close` |
/// | any other element of the schema but Unknown (View, Create, Send, Network...) | `action` of that kind |
public final class TimelineRoles {

    /// A role filled in from the schema's table: filled in again when the event type's element or Action changes.
    public static final String FROM_SCHEMA = "schema";
    /// A role given (the user's answer, proposed by the agent): kept as given.
    public static final String FROM_GIVEN = "given";
    /// The EventDetail elements of the event-logging schema.
    public static final Set<String> ELEMENTS = Set.of("Authenticate", "Authorise", "Process", "Create", "View",
            "Update", "Delete", "Copy", "Move", "Send", "Receive", "Print", "Export", "Import", "Search", "Network",
            "Install", "Uninstall", "Approval", "Alert", "Unknown");
    /// The schema's Authenticate Actions after which the user is active.
    public static final Set<String> ACTIVE_FROM = Set.of("Logon", "ScreenUnlock");
    /// The schema's Authenticate Actions after which the user is idle.
    public static final Set<String> IDLE_FROM = Set.of("Logoff", "ScreenLock");
    /// The schema's Authenticate Actions that start a remote session.
    public static final Set<String> REMOTE_STARTS = Set.of("Connect", "Reconnect");
    /// The schema's Authenticate Actions that end a remote session.
    public static final Set<String> REMOTE_ENDS = Set.of("Disconnect");
    /// The schema's Process Actions that start a process.
    public static final Set<String> STARTS = Set.of("Execute", "Startup");
    /// The schema's Process Actions that end a process.
    public static final Set<String> ENDS = Set.of("Terminate", "Shutdown");

    /// The roles the user is asked for, as the form lists them: a period's with its kind.
    public static final List<String> ASKED_ROLES = TimelineRole.NAMES.stream()
            .map(name -> name.startsWith("period_")
                    ? name + " <kind>"
                    : name)
            .toList();

    private static final String AUTHENTICATE = "Authenticate";
    private static final String PROCESS = "Process";
    private static final String UNKNOWN = "Unknown";

    private TimelineRoles() {
        // Static only.
    }

    /// @param eventDetail An event type's action element, e.g. `Authenticate` or `Network/Permit`, or null.
    /// @param action      Its Action, or null.
    /// @return Its role where the schema's table settles it; empty where it doesn't (no element, `Unknown`, an
    /// element the schema doesn't have, or an `Authenticate` or `Process` Action the table doesn't name).
    public static Optional<TimelineRole> bySchema(final String eventDetail, final String action) {
        final String element = element(eventDetail);
        if (element == null || UNKNOWN.equals(element) || !ELEMENTS.contains(element)) {
            return Optional.empty();
        }
        final String given = action == null || action.isBlank()
                ? null
                : action.strip();
        if (AUTHENTICATE.equals(element)) {
            if (given != null && matches(ACTIVE_FROM, given)) {
                return Optional.of(TimelineRole.ACTIVE);
            }
            if (given != null && matches(IDLE_FROM, given)) {
                return Optional.of(TimelineRole.IDLE);
            }
            if (given != null && matches(REMOTE_STARTS, given)) {
                return Optional.of(TimelineRole.PERIOD_START);
            }
            if (given != null && matches(REMOTE_ENDS, given)) {
                return Optional.of(TimelineRole.PERIOD_END);
            }
            return Optional.empty();
        }
        if (PROCESS.equals(element)) {
            if (given != null && matches(STARTS, given)) {
                return Optional.of(TimelineRole.OPEN);
            }
            if (given != null && matches(ENDS, given)) {
                return Optional.of(TimelineRole.CLOSE);
            }
            return Optional.empty();
        }
        return Optional.of(TimelineRole.ACTION);
    }

    /// @param eventDetail An event type's action element, or null.
    /// @param action      Its Action, or null.
    /// @return The period its schema role starts or ends (a `remote_session` for an Authenticate Connect,
    /// Reconnect or Disconnect, by its kind's default key); null where its schema role is no period's.
    public static TimelinePeriod periodBySchema(final String eventDetail, final String action) {
        return bySchema(eventDetail, action)
                .filter(TimelineRole::isPeriod)
                .map(role -> new TimelinePeriod(TimelinePeriod.REMOTE_SESSION, null))
                .orElse(null);
    }

    /// @param eventDetail An event type's action element, or null.
    /// @param action      Its Action, or null.
    /// @return The kind of action it is on a timeline: the element, with its Action where it has one, lower case,
    /// e.g. `view` or `authenticate changepassword`; `event` with no element.
    public static String kind(final String eventDetail, final String action) {
        final String element = element(eventDetail);
        final String base = element == null
                ? "event"
                : element.toLowerCase(Locale.ROOT);
        return action == null || action.isBlank()
                ? base
                : base + " " + action.strip().toLowerCase(Locale.ROOT);
    }

    /// @param type An event type.
    /// @return It with its role settled: a role given kept; else the schema's, where it settles one; else none.
    public static EventType settled(final EventType type) {
        if (type == null) {
            return null;
        }
        if (type.timelineRole() != null && FROM_GIVEN.equals(type.timelineRoleFrom())) {
            return type;
        }
        final Optional<TimelineRole> schema = bySchema(type.eventDetail(), type.action());
        return schema.map(role -> type.withTimelineRole(role.label(), FROM_SCHEMA,
                        periodBySchema(type.eventDetail(), type.action())))
                .orElseGet(() -> type.withTimelineRole(null, null, null));
    }

    /// @param catalogue A catalogue, or null.
    /// @return It with every event type's role settled ([#settled]); null for null.
    public static Catalogue filled(final Catalogue catalogue) {
        if (catalogue == null) {
            return null;
        }
        return catalogue.withEventTypes(catalogue.eventTypes().stream().map(TimelineRoles::settled).toList());
    }

    /// @param types Event types.
    /// @return Those whose role the schema doesn't settle and no one has given, in order: the user is asked for
    /// theirs. Dropped event types write no events, so need none.
    public static List<EventType> unsettled(final List<EventType> types) {
        final List<EventType> out = new ArrayList<>();
        for (final EventType type : types) {
            final EventType settled = settled(type);
            if (!settled.drop() && settled.timelineRole() == null) {
                out.add(type);
            }
        }
        return out;
    }

    /// Each event type's role as a timeline reads it from the catalogue: given, else the schema's.
    ///
    /// @param catalogue A catalogue, or null.
    /// @return The role of each event type that has one, by TypeId, in the catalogue's order; those with none (to
    /// be classified by the timeline itself) left out, as are dropped ones.
    public static Map<String, Role> byTypeId(final Catalogue catalogue) {
        final Map<String, Role> out = new LinkedHashMap<>();
        if (catalogue == null) {
            return out;
        }
        for (final EventType type : catalogue.eventTypes()) {
            final EventType settled = settled(type);
            if (settled.drop() || settled.typeId() == null) {
                continue;
            }
            TimelineRole.of(settled.timelineRole())
                    // A period role with no kind (a catalogue written by hand) is none.
                    .filter(role -> !role.isPeriod() || settled.timelinePeriod() != null)
                    .ifPresent(role -> out.put(settled.typeId(), new Role(settled.typeId(), role,
                            kind(settled.eventDetail(), settled.action()), settled.timelineRoleFrom(),
                            settled.timelinePeriod())));
        }
        return out;
    }

    /// What a set put to the user says of its roles: those the schema fills in, and those the user is asked for.
    ///
    /// @param set A set of event types.
    /// @return The words, one paragraph; empty for an empty set.
    public static String said(final List<EventType> set) {
        if (set.isEmpty()) {
            return "";
        }
        final List<String> filled = new ArrayList<>();
        final List<String> given = new ArrayList<>();
        final List<String> asked = new ArrayList<>();
        for (final EventType type : set) {
            final EventType settled = settled(type);
            if (settled.drop()) {
                continue;
            }
            if (settled.timelineRole() == null) {
                asked.add(settled.typeId());
            } else if (FROM_GIVEN.equals(settled.timelineRoleFrom())) {
                given.add(settled.typeId() + " " + roleSaid(settled));
            } else {
                filled.add(settled.typeId() + " " + roleSaid(settled));
            }
        }
        final StringBuilder out = new StringBuilder("Timeline roles");
        if (!filled.isEmpty()) {
            out.append(" from the schema: ").append(String.join(", ", filled)).append(".");
        }
        if (!given.isEmpty()) {
            out.append(filled.isEmpty()
                    ? ": "
                    : " Given: ").append(String.join(", ", given)).append(".");
        }
        if (!asked.isEmpty()) {
            out.append(filled.isEmpty() && given.isEmpty()
                    ? ": "
                    : " ").append("Not settled by the schema: ").append(String.join(", ", asked))
                    .append(". Say each one's role (")
                    .append(String.join(", ", ASKED_ROLES))
                    .append(") in your amendments, or leave it for the timeline to judge.");
        }
        out.append(" Name any that marks a window gaining focus as focus, and any that starts or ends a period (a "
                   + "door entered or left, a device inserted or removed, a call joined or left) as period_start or "
                   + "period_end with its kind (").append(String.join(", ", TimelinePeriod.KINDS))
                .append(", or your own).");
        return out.toString();
    }

    /// What a set's own form asks of its roles (decided with the user 2026-11-25: asked in the set's form): the
    /// event types whose role the schema doesn't settle and no one has given, for the user to say in their answer,
    /// and those given, for them to change. Seen in eval case 109 on Sonnet: asked only in the table the agent was
    /// to show first, which it didn't, so the user accepted the set never asked.
    ///
    /// @param set A set of event types.
    /// @return A line for those asked, then one for those given, each where there are any; none where the schema
    /// settles every role (dropped event types need none).
    public static List<String> askedInForm(final List<EventType> set) {
        final List<String> asked = new ArrayList<>();
        final List<String> given = new ArrayList<>();
        for (final EventType type : set) {
            final EventType settled = settled(type);
            if (settled.drop()) {
                continue;
            }
            if (settled.timelineRole() == null) {
                asked.add(settled.typeId());
            } else if (FROM_GIVEN.equals(settled.timelineRoleFrom())) {
                given.add(settled.typeId() + " " + roleText(settled));
            }
        }
        final List<String> lines = new ArrayList<>();
        if (!asked.isEmpty()) {
            final boolean one = asked.size() == 1;
            lines.add(String.join(", ", asked) + ": the schema doesn't settle " + (one
                    ? "it; say its role"
                    : "them; say each one's role") + " in your answer (" + String.join(", ", ASKED_ROLES)
                      + "), or leave it for the timeline to judge");
        }
        if (!given.isEmpty()) {
            lines.add("Given: " + String.join(", ", given) + " (yours to change in your answer)");
        }
        return lines;
    }

    // Its role in words: an action with its kind, e.g. `action (view)`; a period's with its kind, e.g.
    // `period_start (remote_session)`.
    private static String roleSaid(final EventType type) {
        if (TimelineRole.ACTION.label().equals(type.timelineRole())) {
            return "action (" + kind(type.eventDetail(), type.action()) + ")";
        }
        return type.timelinePeriod() == null
                ? type.timelineRole()
                : type.timelineRole() + " (" + type.timelinePeriod().kind() + (type.timelinePeriod().key() == null
                        ? ""
                        : " by " + type.timelinePeriod().key()) + ")";
    }

    // Its role as the user gives it, e.g. `period_start on_site`.
    private static String roleText(final EventType type) {
        return type.timelinePeriod() == null
                ? type.timelineRole()
                : type.timelinePeriod().text(TimelineRole.of(type.timelineRole()).orElseThrow());
    }

    // The element of an action element's path, as the schema spells it; null for none.
    private static String element(final String eventDetail) {
        if (eventDetail == null || eventDetail.isBlank()) {
            return null;
        }
        final String first = eventDetail.strip().split("/", 2)[0].strip();
        return ELEMENTS.stream().filter(known -> known.equalsIgnoreCase(first)).findFirst().orElse(first);
    }

    private static boolean matches(final Set<String> names, final String given) {
        return names.stream().anyMatch(given::equalsIgnoreCase);
    }


    // --------------------------------------------------------------------------------


    /// An event type's role as a timeline reads it.
    ///
    /// @param typeId The event type's TypeId.
    /// @param role   Its role.
    /// @param kind   The kind of action it is ([#kind]).
    /// @param from   [#FROM_SCHEMA] or [#FROM_GIVEN].
    /// @param period Its period, for a period role; else null.
    public record Role(String typeId, TimelineRole role, String kind, String from, TimelinePeriod period) {

        /// A role that is no period's.
        ///
        /// @param typeId The event type's TypeId.
        /// @param role   Its role.
        /// @param kind   The kind of action it is.
        /// @param from   Where it came from.
        public Role(final String typeId, final TimelineRole role, final String kind, final String from) {
            this(typeId, role, kind, from, null);
        }
    }
}
