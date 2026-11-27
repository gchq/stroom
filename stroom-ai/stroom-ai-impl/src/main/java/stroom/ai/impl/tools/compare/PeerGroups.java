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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.ai.impl.tools.streams.StreamSummaries;
import stroom.ai.impl.tools.timeline.TimelineEventXml;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/// Finds a person's peers for `compare_activity`: the people active in a source (one aggregate by the person or user
/// field), then each one's group read from ONE of their events' Event XML (`EventSource/User/UserDetails/<element>`,
/// `Organisation` by default, as processing decorates it there and not in the index), capped; the map of person to
/// group kept in the workspace and reused by later calls. Only elements that say where someone works are taken
/// ([#ELEMENTS]): never `Nationality` or any other personal characteristic.
class PeerGroups {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PeerGroups.class);

    /// The `UserDetails` elements a peer group may be by.
    static final Set<String> ELEMENTS = Set.of("Organisation", "Unit", "Group", "Role");
    /// The default element.
    static final String DEFAULT_ELEMENT = "Organisation";
    /// The workspace file holding each person's group, by element, then field, then person.
    static final String CACHE = "/outputs/compare/peer-groups.json";
    private static final int MAX_EVENT_BYTES = 1_000_000;

    private final TimelineEventXml eventXml;

    /// @param eventXml Reads the events' XML.
    @Inject
    PeerGroups(final TimelineEventXml eventXml) {
        this.eventXml = eventXml;
    }

    /// Finds the person's group and its members among the people active in a source.
    ///
    /// @param session   The session, for the audit log.
    /// @param workspace The chat's workspace, holding the groups read before.
    /// @param element   The `UserDetails` element grouped by.
    /// @param field     The field the people are told apart by.
    /// @param person    The person's values of it.
    /// @param active    The people active, with one event's ids each (`one_event`: `StreamId:EventId`).
    /// @param cap       The most people whose events are read in this call.
    /// @param stopped   True once the user has stopped the call.
    /// @return The person's group and its other members, and what was read.
    Found find(final ToolSession session,
               final SessionWorkspace workspace,
               final String element,
               final String field,
               final List<String> person,
               final Table active,
               final int cap,
               final BooleanSupplier stopped) {
        final Map<String, Map<String, Map<String, String>>> cache = readCache(workspace);
        final Map<String, String> groups = cache.computeIfAbsent(element, k -> new TreeMap<>())
                .computeIfAbsent(field, k -> new TreeMap<>());
        final Map<String, String> wanted = new LinkedHashMap<>();
        for (final Map<String, String> row : active.rows()) {
            final String who = row.get(AggregateQuery.groupColumn(0));
            final String ids = row.get(ActivityProfiles.ONE_EVENT);
            if (NullSafe.isNonBlankString(who) && !groups.containsKey(who) && NullSafe.isNonBlankString(ids)) {
                wanted.put(who, ids);
            }
        }
        // The person first, then the rest up to the cap.
        final Map<String, String> toRead = new LinkedHashMap<>();
        person.stream().filter(wanted::containsKey).forEach(p -> toRead.put(p, wanted.get(p)));
        for (final Map.Entry<String, String> entry : wanted.entrySet()) {
            if (toRead.size() >= cap) {
                break;
            }
            toRead.putIfAbsent(entry.getKey(), entry.getValue());
        }
        final Map<Long, List<Long>> events = new LinkedHashMap<>();
        final Map<String, String> whoByEvent = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : toRead.entrySet()) {
            final String[] parts = entry.getValue().split(":");
            try {
                final long streamId = Long.parseLong(parts[0].strip());
                final long eventId = Long.parseLong(parts[1].strip());
                events.computeIfAbsent(streamId, k -> new ArrayList<>()).add(eventId);
                whoByEvent.put(streamId + ":" + eventId, entry.getKey());
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the event ids " + entry.getValue(), e);
            }
        }
        int read = 0;
        boolean canViewData = true;
        if (!events.isEmpty()) {
            final TimelineEventXml.Read xml = eventXml.read(session, events, MAX_EVENT_BYTES, stopped, count -> {
            }, "for the peer groups of a comparison of activity (compare_activity)");
            canViewData = xml.canViewData();
            for (final Map.Entry<String, String> event : xml.xml().entrySet()) {
                final String who = whoByEvent.get(event.getKey());
                if (who != null) {
                    // An empty group is kept too, so it isn't read again.
                    groups.put(who, NullSafe.string(groupOf(event.getValue(), element)));
                    read++;
                }
            }
            writeCache(workspace, cache);
        }
        final Set<String> personGroups = new LinkedHashSet<>();
        person.stream().map(groups::get).filter(NullSafe::isNonBlankString).forEach(personGroups::add);
        final List<String> members = new ArrayList<>();
        final Set<String> activeNames = new LinkedHashSet<>();
        active.rows().forEach(row -> activeNames.add(row.get(AggregateQuery.groupColumn(0))));
        for (final String who : activeNames) {
            if (who != null && !person.contains(who) && personGroups.contains(groups.get(who))) {
                members.add(who);
            }
        }
        final int unknown = (int) activeNames.stream().filter(who -> who != null && !groups.containsKey(who)).count();
        return new Found(List.copyOf(personGroups), members, read, unknown, canViewData);
    }

    /// Reads a `UserDetails` element's value from an event's XML.
    ///
    /// @param xml     The event (inside its stream's header and footer).
    /// @param element The element under `EventSource/User/UserDetails`.
    /// @return Its text, or null if it isn't there or the XML can't be read.
    static String groupOf(final String xml, final String element) {
        try {
            final Document document = StreamSummaries.documentBuilder().parse(new InputSource(new StringReader(xml)));
            for (final Element event : children(document.getDocumentElement(), "Event")) {
                for (final Element source : children(event, "EventSource")) {
                    for (final Element user : children(source, "User")) {
                        for (final Element details : children(user, "UserDetails")) {
                            for (final Element value : children(details, element)) {
                                final String text = NullSafe.trim(value.getTextContent());
                                if (!text.isEmpty()) {
                                    return text;
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.debug(() -> "Unable to read an event's XML for its " + element, e);
        }
        return null;
    }

    private static List<Element> children(final Element parent, final String localName) {
        final List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof final Element element
                && localName.equals(NullSafe.nonBlankStringElse(element.getLocalName(), element.getTagName()))) {
                children.add(element);
            }
        }
        return children;
    }

    private static Map<String, Map<String, Map<String, String>>> readCache(final SessionWorkspace workspace) {
        final WorkspacePath path = WorkspacePath.parse(CACHE);
        if (workspace.find(path).isEmpty()) {
            return new TreeMap<>();
        }
        try {
            final String text = Files.readString(workspace.localFile(path), StandardCharsets.UTF_8);
            final Map<String, Map<String, Map<String, String>>> cache = JsonUtil.getMapper().readValue(text,
                    new TypeReference<Map<String, Map<String, Map<String, String>>>>() {
                    });
            return cache == null
                    ? new TreeMap<>()
                    : new TreeMap<>(cache);
        } catch (final IOException | RuntimeException e) {
            // A file the user edited into something else is read again from the events.
            LOGGER.debug(() -> "Unable to read " + CACHE, e);
            return new TreeMap<>();
        }
    }

    private static void writeCache(final SessionWorkspace workspace,
                                   final Map<String, Map<String, Map<String, String>>> cache) {
        workspace.write(WorkspacePath.parse(CACHE), JsonUtil.writeValueAsString(cache, true), null);
    }


    // --------------------------------------------------------------------------------


    /// A person's group in one source.
    ///
    /// @param groups      The person's group(s) (several where their values were in more than one).
    /// @param members     The other people active in the source in the same group.
    /// @param read        How many people's events were read in this call.
    /// @param unknown     How many active people's groups aren't known (past the cap, or unreadable).
    /// @param canViewData False if the user may not view data, so no event was read.
    record Found(List<String> groups, List<String> members, int read, int unknown, boolean canViewData) {

    }
}
