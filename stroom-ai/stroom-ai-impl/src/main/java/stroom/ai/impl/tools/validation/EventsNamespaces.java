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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String XPATH_DEFAULT_NAMESPACE = "xpath-default-namespace";
    private static final Pattern QNAME_PREFIX = Pattern.compile("([A-Za-z_][\\w.\\-]*):[A-Za-z_*]");

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

    /// The Events namespace an XSLT writes Events in. Where it writes an `Events` or `Event` element
    /// ([#writesEventsOrEvent]), that of the first literal `Events` or `Event`, or element under one, in one of the
    /// namespaces given. Where it writes neither, but reads Events ([#readsEvents]: an output, e.g. a flattener
    /// handing `<records><record><data .../></record></records>` on to the next XSLT in the namespace that XSLT
    /// reads), none: what it writes isn't Events, whatever namespace it is in. Otherwise that of its first literal
    /// result element (any element not in the XSLT namespace) in one of the namespaces given: a shared XSLT may write
    /// only part of an Event (`<EventSource>`, say) from records, so any element counts.
    ///
    /// @param xslt       An XSLT's text.
    /// @param namespaces The Events namespaces to look for: `event-logging:3` and this Stroom's forks.
    /// @return The namespace, or empty if it writes Events in none of them (or isn't well-formed).
    public static Optional<String> writtenBy(final String xslt, final Collection<String> namespaces) {
        final Element root;
        try {
            root = XmlTrees.parse(XsltVersionHistory.strip(Objects.requireNonNullElse(xslt, "")));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        final List<Element> written = XmlTrees.descendantsAndSelf(root).stream()
                .filter(element -> element.getNamespaceURI() != null
                                   && !XmlTrees.XSL.equals(element.getNamespaceURI())
                                   && namespaces.contains(element.getNamespaceURI()))
                .toList();
        if (writesEventsOrEvent(root)) {
            final Optional<String> inEvents = written.stream()
                    .filter(element -> isEvents(element) || underEvents(element))
                    .map(Element::getNamespaceURI)
                    .findFirst();
            if (inEvents.isPresent()) {
                return inEvents;
            }
        } else if (readsEvents(root, namespaces)) {
            return Optional.empty();
        }
        return written.stream()
                .map(Element::getNamespaceURI)
                .findFirst();
    }

    /// Whether an XSLT writes an `Events` or `Event` element, in any namespace: a literal result element of that
    /// name, an `xsl:element` of that name, or an `xsl:copy` (of what it reads, Events: a decoration's identity
    /// template). A translation or a decoration does; a flattener, an indexing XSLT or one writing Kafka records
    /// doesn't.
    ///
    /// @param root An XSLT's root element.
    /// @return True if it writes one.
    public static boolean writesEventsOrEvent(final Element root) {
        for (final Element element : XmlTrees.descendantsAndSelf(root)) {
            if (!XmlTrees.XSL.equals(element.getNamespaceURI())) {
                if (isEvents(element)) {
                    return true;
                }
            } else if (XmlTrees.isXsl(element, "copy")) {
                return true;
            } else if (XmlTrees.isXsl(element, "element")) {
                final String name = element.getAttribute("name").strip();
                final String local = name.substring(name.indexOf(':') + 1);
                if (EVENTS.equals(local) || EVENT.equals(local)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Whether an XSLT reads an Events namespace: an `xpath-default-namespace` on any of its elements (or
    /// `xsl:xpath-default-namespace` on a literal result element) is one, or a template's match names an element by
    /// a prefix bound to one. An intermediate stage reading another's output in an Events namespace (`records/data`
    /// in `event-logging:3`) reads one too.
    ///
    /// @param root       An XSLT's root element.
    /// @param namespaces The Events namespaces: `event-logging:3` and this Stroom's forks.
    /// @return True if it reads one.
    public static boolean readsEvents(final Element root, final Collection<String> namespaces) {
        for (final Element element : XmlTrees.descendantsAndSelf(root)) {
            final String declared = XmlTrees.XSL.equals(element.getNamespaceURI())
                    ? element.getAttribute(XPATH_DEFAULT_NAMESPACE)
                    : element.getAttributeNS(XmlTrees.XSL, XPATH_DEFAULT_NAMESPACE);
            if (!declared.isBlank() && namespaces.contains(declared.strip())) {
                return true;
            }
            if (XmlTrees.isXsl(element, "template") && element.hasAttribute("match")) {
                final Map<String, String> bound = XmlTrees.namespaces(element);
                final Matcher prefix = QNAME_PREFIX.matcher(element.getAttribute("match"));
                while (prefix.find()) {
                    if (namespaces.contains(bound.get(prefix.group(1)))) {
                        return true;
                    }
                }
            }
        }
        return false;
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

    // Whether a literal result element is inside a literal Events or Event.
    private static boolean underEvents(final Element element) {
        Node node = element.getParentNode();
        while (node instanceof final Element parent) {
            if (!XmlTrees.XSL.equals(parent.getNamespaceURI()) && isEvents(parent)) {
                return true;
            }
            node = parent.getParentNode();
        }
        return false;
    }

    /// @param node   An element.
    /// @param parent Another element.
    /// @return True if the element is in the same namespace as the other (both in none counts as the same).
    public static boolean sameNamespace(final Node node, final Node parent) {
        return Objects.equals(node.getNamespaceURI(), parent.getNamespaceURI());
    }
}
