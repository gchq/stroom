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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// What an event means to an activity timeline, from its event-logging Events XML (or, where the XML is gone, from
/// the element and Action an index holds): the EventDetail element and its Action give its role, the objects it
/// names its label, and the values under the element the keys pairs are matched by. Only the schema's own names are
/// known here (its EventDetail elements and the Actions of `Authenticate` and `Process`); nothing of any source.
///
/// | Element and Action | Role |
/// | --- | --- |
/// | Authenticate Logon, ScreenUnlock, Connect, Reconnect | active from here (`idle_end`) |
/// | Authenticate Logoff, ScreenLock, Disconnect | idle from here (`idle_start`) |
/// | Process Execute, Startup | opened (`open`) |
/// | Process Terminate, Shutdown | closed (`close`) |
/// | any other (View, Create, Send, Network...) | an action of that kind |
///
/// The user and the time are never read from the XML: an indexing pipeline may normalise them (lowercased, a domain
/// stripped), so the index's are the timeline's.
final class EventXmlClassifier {

    /// EventDetail's children that aren't the action element.
    static final Set<String> NOT_ACTIONS = Set.of("TypeId", "Description", "Classification", "Purpose");
    /// The EventDetail elements of the event-logging schema.
    static final Set<String> ELEMENTS = TimelineRoles.ELEMENTS;
    /// The schema's Authenticate Actions after which the user is active.
    static final Set<String> ACTIVE_FROM = TimelineRoles.ACTIVE_FROM;
    /// The schema's Authenticate Actions after which the user is idle.
    static final Set<String> IDLE_FROM = TimelineRoles.IDLE_FROM;
    /// The schema's Process Actions that start a process.
    static final Set<String> STARTS = TimelineRoles.STARTS;
    /// The schema's Process Actions that end a process.
    static final Set<String> ENDS = TimelineRoles.ENDS;
    /// The schema's leaves that name what an event touched, the most telling first.
    static final List<String> OBJECTS = List.of("Title", "Subject", "URL", "Path", "Name");
    /// The schema's elements whose events go from a source to a destination: their object is the destination's
    /// device (its HostName, else its IPAddress), for a Network event first, for Send and Receive after the object
    /// leaves (a subject, a URL); the source and destination devices' own names are never the object.
    static final Set<String> TO_DESTINATION = Set.of("Network", "Send", "Receive");
    private static final String NETWORK = "Network";
    private static final Set<String> ENDPOINTS = Set.of("Source", "Destination");
    /// The longest value kept as a key.
    static final int MAX_KEY_CHARS = 200;
    private static final String AUTHENTICATE = "Authenticate";
    private static final String USER = "User";
    private static final String DATA = "Data";
    private static final String PROCESS = "Process";

    private EventXmlClassifier() {
        // Static only.
    }

    /// @param xml One event's Events XML: an `Events` document holding it, or the `Event` element alone.
    /// @return What it means, or empty if it isn't an event with an EventDetail (or doesn't parse).
    static Optional<Classified> classify(final String xml) {
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        final XmlElement root;
        try {
            root = XmlParser.parse(xml, true, true);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        final XmlElement event = "Event".equals(root.localName())
                ? root
                : root.iterElements().stream()
                        .filter(element -> "Event".equals(element.localName()))
                        .findFirst()
                        .orElse(null);
        final XmlElement detail = child(event, "EventDetail");
        if (detail == null) {
            return Optional.empty();
        }
        final XmlElement action = detail.childElements().stream()
                .filter(element -> !NOT_ACTIONS.contains(element.localName()))
                .findFirst()
                .orElse(null);
        if (action == null) {
            return Optional.empty();
        }
        final String element = action.localName();
        final String actionName = text(child(action, "Action"));
        final String typeId = text(child(detail, "TypeId"));
        final Map<String, String> keys = new LinkedHashMap<>();
        leaves(action, "", keys);
        keys.remove("Action");
        // The element's own user (an Authenticate's) is the user's, as the EventSource's is: not a key.
        keys.keySet().removeIf(path -> path.startsWith(USER + "/"));
        // Every other leaf of the event but its time, its user and its type: the EventSource's (a client, a
        // session's Data), and EventDetail's beside the element, by their whole paths.
        final XmlElement source = child(event, "EventSource");
        if (source != null) {
            for (final XmlElement part : source.childElements()) {
                if (!USER.equals(part.localName())) {
                    leaf(part, "EventSource", keys);
                }
            }
        }
        for (final XmlElement part : detail.childElements()) {
            if (part != action && !"TypeId".equals(part.localName()) && !"Description".equals(part.localName())) {
                leaf(part, "EventDetail", keys);
            }
        }
        final XmlElement device = child(source, "Device");
        final String host = firstNonBlank(text(child(device, "HostName")), text(child(device, "Name")));
        final String application = PROCESS.equals(element)
                ? baseName(keys.get("Command"))
                : null;
        return Optional.of(of(element, actionName, typeId, application, object(element, action), host, keys,
                Route.XML));
    }

    /// @param element  The EventDetail element, e.g. `Authenticate`.
    /// @param action   Its Action, or null.
    /// @param typeId   The event's TypeId, or null.
    /// @param application The application, or null.
    /// @param object   What the event touched, or null.
    /// @param host     The host, or null.
    /// @param keys     The values under the element by their paths below it.
    /// @param route    How it was classified.
    /// @return It classified by the schema's element and Action.
    static Classified of(final String element,
                         final String action,
                         final String typeId,
                         final String application,
                         final String object,
                         final String host,
                         final Map<String, String> keys,
                         final Route route) {
        final String spelt = spelt(element);
        final Role role = role(spelt, action);
        return new Classified(spelt, blankToNull(action), blankToNull(typeId), role, kind(spelt, action),
                application, object, host, keys, route);
    }

    /// @param element An EventDetail element, as an index may hold it (any case).
    /// @return It as the schema spells it, or as given if the schema has no such element.
    static String spelt(final String element) {
        if (element == null) {
            return null;
        }
        final String given = element.strip();
        return ELEMENTS.stream().filter(known -> known.equalsIgnoreCase(given)).findFirst().orElse(given);
    }

    /// @param value A value.
    /// @return True if it names one of the schema's EventDetail elements, in any case.
    static boolean isElement(final String value) {
        return value != null && ELEMENTS.stream().anyMatch(known -> known.equalsIgnoreCase(value.strip()));
    }

    /// @param element The EventDetail element, as the schema spells it.
    /// @param action  Its Action, or null.
    /// @return Its role, by the schema's table.
    static Role role(final String element, final String action) {
        final String spelt = action == null
                ? null
                : action.strip();
        if (AUTHENTICATE.equals(element) && spelt != null) {
            if (ACTIVE_FROM.stream().anyMatch(spelt::equalsIgnoreCase)) {
                return Role.IDLE_END;
            }
            if (IDLE_FROM.stream().anyMatch(spelt::equalsIgnoreCase)) {
                return Role.IDLE_START;
            }
        }
        if (PROCESS.equals(element) && spelt != null) {
            if (STARTS.stream().anyMatch(spelt::equalsIgnoreCase)) {
                return Role.OPEN;
            }
            if (ENDS.stream().anyMatch(spelt::equalsIgnoreCase)) {
                return Role.CLOSE;
            }
        }
        return Role.ACTION;
    }

    // An action's kind: the element, and its Action where it has one, e.g. 'view', 'authenticate other'.
    private static String kind(final String element, final String action) {
        final String base = element == null
                ? "event"
                : element.toLowerCase(Locale.ROOT);
        return action == null || action.isBlank()
                ? base
                : base + " " + action.strip().toLowerCase(Locale.ROOT);
    }

    // What the event touched: the most telling of the schema's object leaves under the action element; for a
    // Network event its destination first, for Send and Receive its destination where no leaf tells more.
    private static String object(final String element, final XmlElement action) {
        if (!TO_DESTINATION.contains(element)) {
            return objectLeaf(action, false);
        }
        final String destination = destination(action);
        if (NETWORK.equals(element) && destination != null) {
            return destination;
        }
        final String leaf = objectLeaf(action, true);
        return leaf != null
                ? leaf
                : destination;
    }

    // The most telling object leaf, leaving out those under a Source or Destination where asked.
    private static String objectLeaf(final XmlElement action, final boolean skipEndpoints) {
        for (final String name : OBJECTS) {
            final String found = objectLeaf(action, name, skipEndpoints);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String objectLeaf(final XmlElement parent, final String name, final boolean skipEndpoints) {
        for (final XmlElement child : parent.childElements()) {
            if (child.childElements().isEmpty()) {
                if (name.equals(child.localName()) && text(child) != null) {
                    return text(child);
                }
            } else if (!skipEndpoints || !ENDPOINTS.contains(child.localName())) {
                final String found = objectLeaf(child, name, skipEndpoints);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // The first Destination's device below the element: its HostName, else its IPAddress (the schema's
    // Destination holds a Device; one written straight under it is read too).
    private static String destination(final XmlElement parent) {
        for (final XmlElement child : parent.childElements()) {
            if ("Destination".equals(child.localName())) {
                final XmlElement device = child(child, "Device");
                final XmlElement holder = device != null
                        ? device
                        : child;
                return firstNonBlank(text(child(holder, "HostName")), text(child(holder, "IPAddress")));
            }
            if (!child.childElements().isEmpty() && !"Source".equals(child.localName())) {
                final String found = destination(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Every leaf below the element, by its path below it (the first of a path kept); a Data entry by its Name, as
    // `Data[SessionId]`.
    private static void leaves(final XmlElement element, final String path, final Map<String, String> out) {
        for (final XmlElement child : element.childElements()) {
            leaf(child, path, out);
        }
    }

    private static void leaf(final XmlElement child, final String path, final Map<String, String> out) {
        final String name = DATA.equals(child.localName()) && blankToNull(child.get("Name")) != null
                ? DATA + "[" + child.get("Name").strip() + "]"
                : child.localName();
        final String childPath = path.isEmpty()
                ? name
                : path + "/" + name;
        if (child.childElements().isEmpty()) {
            final String text = DATA.equals(child.localName())
                    ? blankToNull(child.get("Value"))
                    : text(child);
            if (text != null && text.length() <= MAX_KEY_CHARS) {
                out.putIfAbsent(childPath, text);
            }
        } else {
            leaves(child, childPath, out);
        }
    }

    private static XmlElement child(final XmlElement parent, final String name) {
        if (parent == null) {
            return null;
        }
        return parent.childElements().stream()
                .filter(element -> name.equals(element.localName()))
                .findFirst()
                .orElse(null);
    }

    private static String text(final XmlElement element) {
        if (element == null) {
            return null;
        }
        return blankToNull(element.text());
    }

    private static String blankToNull(final String text) {
        if (text == null) {
            return null;
        }
        final String stripped = text.strip();
        return stripped.isEmpty()
                ? null
                : stripped;
    }

    private static String firstNonBlank(final String a, final String b) {
        return a != null
                ? a
                : b;
    }

    /// @param path A path or command, e.g. `C:\Program Files\Office\WINWORD.EXE /n`.
    /// @return Its last part, without arguments, e.g. `WINWORD.EXE`; null for null.
    static String baseName(final String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String command = path.strip();
        if (command.startsWith("\"") && command.indexOf('"', 1) > 0) {
            command = command.substring(1, command.indexOf('"', 1));
        } else {
            // Its arguments, where they start as arguments do (` /n`, ` -x`), aren't its name.
            for (final String argument : new String[]{" /", " -"}) {
                final int at = command.indexOf(argument);
                if (at > 0) {
                    command = command.substring(0, at);
                }
            }
        }
        final int slash = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        String name = command.substring(slash + 1);
        final int space = name.indexOf(' ');
        if (space > 0 && slash < 0) {
            name = name.substring(0, space);
        }
        return name.isBlank()
                ? null
                : name;
    }


    // --------------------------------------------------------------------------------


    /// How an event's role was found.
    enum Route {
        /// From its Events XML.
        XML("Events XML"),
        /// From the element and Action the index holds.
        INDEX_FIELDS("index fields"),
        /// From the source's own event code, classified once and confirmed.
        EVENT_CODE("event code"),
        /// By the call's (or skill's) roles.
        ROLES("roles given"),
        /// Not classified: an action of the source.
        NONE("not classified");

        private final String description;

        Route(final String description) {
            this.description = description;
        }

        /// @return Its name in the result.
        String description() {
            return description;
        }
    }


    // --------------------------------------------------------------------------------


    /// An event as classified.
    ///
    /// @param element     The EventDetail element, e.g. `Authenticate`, or null.
    /// @param action      Its Action, e.g. `Logon`, or null.
    /// @param typeId      The event's TypeId (the source's own name for its type), or null.
    /// @param role        Its role.
    /// @param kind        An action's kind, e.g. `view`.
    /// @param application The application, or null.
    /// @param object      What it touched (a title, subject, URL, path or name), or null.
    /// @param host        The host the XML names, or null.
    /// @param keys        The event's correlation keys: the values under the element by their paths below it (e.g.
    ///                    `ProcessId`, `Data[SessionId]`), the EventSource's and EventDetail's other leaves by their
    ///                    whole paths (e.g. `EventSource/Client/IPAddress`), never its time, user or type; and, from
    ///                    [TimelineClassification], the index's fields returned with the event, by their names.
    /// @param route       How it was classified.
    record Classified(String element,
                      String action,
                      String typeId,
                      Role role,
                      String kind,
                      String application,
                      String object,
                      String host,
                      Map<String, String> keys,
                      Route route) {

        /// Copies the map.
        Classified {
            keys = keys == null
                    ? Map.of()
                    : Map.copyOf(keys);
        }

        /// @return Its type by the schema: its element and Action, e.g. `Authenticate/Logon`.
        String schemaType() {
            if (element == null) {
                return null;
            }
            return action == null
                    ? element
                    : element + "/" + action;
        }

        /// @return Its type, as pairs are found by: its TypeId where it has one (the source's own), else its
        /// schema type.
        String type() {
            return typeId != null
                    ? typeId
                    : schemaType();
        }

        /// @param type A type, by TypeId or by element and Action.
        /// @return True if it is this event's.
        boolean isType(final String type) {
            return type != null && (type.equalsIgnoreCase(String.valueOf(typeId))
                                    || type.equalsIgnoreCase(String.valueOf(schemaType())));
        }

        /// @param more More keys (an index's fields), each kept only where it has no value by that name.
        /// @return It with them.
        Classified withKeys(final Map<String, String> more) {
            if (more == null || more.isEmpty()) {
                return this;
            }
            final Map<String, String> merged = new LinkedHashMap<>(keys);
            more.forEach(merged::putIfAbsent);
            return new Classified(element, action, typeId, role, kind, application, object, host, merged, route);
        }

        /// @param role A role in its place.
        /// @return It with that role (an override).
        Classified withRole(final Role role) {
            return new Classified(element, action, typeId, role, kind, application, object, host, keys, route);
        }
    }
}
