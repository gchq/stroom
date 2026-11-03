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

package stroom.ai.impl.tools.validation;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The namespace Events are written in. Most environments write Stroom's standard `event-logging:3`, but some hold
/// a fork of the event-logging schema in a namespace of their own (seen: `file://xml/schema/accounting/events`),
/// with the same elements. Seen in testing: Events in such a namespace were profiled as no events at all, so an
/// index plan found no populated paths and check_events refused them as "not an Events document".
///
/// An Events document is told by its shape, `<Events>` holding `<Event>`s in one namespace, not by its namespace.
public final class EventsNamespaces {

    /// Stroom's standard event-logging namespace (of every version, 4.x as much as 3.x).
    public static final String STANDARD = HeldSchemas.EVENT_LOGGING_NS;
    /// What the agent is told about a schema filter's schema group when Events are in a forked namespace. Seen in
    /// testing: the agent believed moving Events between a forked namespace and event-logging:3 needed the schema
    /// filter's schema group changed, and stopped to ask.
    public static final String SCHEMA_GROUP_NOTE = "A schema filter's schema group (e.g. EVENTS) only narrows the "
                                                   + "schemas it may use: the output's own namespace and "
                                                   + "xsi:schemaLocation pick the schema, so one group holding "
                                                   + "both event-logging:3 and a forked schema validates either, "
                                                   + "and changing namespace needs no change to the schema filter.";

    // Output namespaces that are never Events.
    private static final Set<String> NOT_EVENTS = Set.of(XmlTrees.XSL, "records:2", "reference-data:2",
            "kafka-records:1", "kafka-records:2", "http://www.w3.org/2005/xpath-functions",
            "http://www.w3.org/2013/XSL/json", "stroom");
    private static final String EVENTS = "Events";
    private static final String EVENT = "Event";

    private EventsNamespaces() {
        // Static only.
    }

    /// @param root A document's root element.
    /// @return True if it is an Events document (an `<Events>` root, or a single `<Event>`), in any namespace.
    public static boolean isEvents(final Element root) {
        return root != null && (EVENTS.equals(root.getLocalName()) || EVENT.equals(root.getLocalName()));
    }

    /// @param root A document's root element.
    /// @return The namespace of its Events (that of its `<Events>` or `<Event>` root), "" for none, or null if it
    /// is not an Events document.
    public static String of(final Element root) {
        if (!isEvents(root)) {
            return null;
        }
        return Objects.requireNonNullElse(root.getNamespaceURI(), "");
    }

    /// @param namespace An Events namespace.
    /// @return True if it is Stroom's standard event-logging namespace.
    public static boolean isStandard(final String namespace) {
        return STANDARD.equals(namespace);
    }

    /// @param namespace An Events namespace, or null.
    /// @return The namespace, or the standard one if it is null or blank.
    public static String orStandard(final String namespace) {
        return namespace == null || namespace.isBlank()
                ? STANDARD
                : namespace;
    }

    /// The Events namespace an XSLT writes in: that of its first literal result element (any element not in the
    /// XSLT namespace) in one of the namespaces given. A shared XSLT may write only part of an Event
    /// (`<EventSource>`, say), so any element counts, not just `<Events>` and `<Event>`.
    ///
    /// @param xslt       An XSLT's text.
    /// @param namespaces The Events namespaces to look for: `event-logging:3` and this Stroom's forks.
    /// @return The namespace, or empty if it writes in none of them (or isn't well-formed).
    public static Optional<String> writtenBy(final String xslt, final Collection<String> namespaces) {
        final Element root;
        try {
            root = XmlTrees.parse(XsltVersionHistory.strip(Objects.requireNonNullElse(xslt, "")));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        return XmlTrees.descendantsAndSelf(root).stream()
                .map(Element::getNamespaceURI)
                .filter(Objects::nonNull)
                .filter(namespace -> !XmlTrees.XSL.equals(namespace) && namespaces.contains(namespace))
                .findFirst();
    }

    /// The namespaces an XSLT writes Events elements in: `event-logging:3`, that of any `<Events>` or `<Event>` it
    /// writes, and its default namespace unless that is one Events are never in (records, reference data, JSON,
    /// ...). A shared XSLT of an environment's fork may write only parts of an Event (`<EventSource>`), in the fork's
    /// namespace, declared as its default.
    ///
    /// @param root An XSLT's root element.
    /// @return The namespaces.
    public static Set<String> eventsNamespacesOf(final Element root) {
        final Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add(STANDARD);
        for (final Element element : XmlTrees.descendantsAndSelf(root)) {
            if (isEvents(element) && element.getNamespaceURI() != null
                && !XmlTrees.XSL.equals(element.getNamespaceURI())) {
                namespaces.add(element.getNamespaceURI());
            }
        }
        final String declared = root.lookupNamespaceURI(null);
        if (declared != null && !declared.isEmpty() && !NOT_EVENTS.contains(declared)) {
            namespaces.add(declared);
        }
        return namespaces;
    }

    /// @param node   An element.
    /// @param parent Another element.
    /// @return True if the element is in the same namespace as the other (both in none counts as the same).
    public static boolean sameNamespace(final Node node, final Node parent) {
        return Objects.equals(node.getNamespaceURI(), parent.getNamespaceURI());
    }
}
