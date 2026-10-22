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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.validation.SchemaErrors.SchemaError;
import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;

import jakarta.inject.Inject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/// Checks of event XML: against the event-logging XSD this Stroom holds, and against the quality rules beyond the
/// schema. Ported from the Stroom MCP server's `tools/validation.py` (`validate_events`, `check_event_quality` and
/// `_event_findings`), keeping the shape of their results, which `check_events` gives.
public class EventChecks {

    private static final Pattern ISO_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z$");
    // EventDetail children that describe the event rather than being its action.
    private static final Set<String> DETAIL_META = Set.of("TypeId", "Description", "Classification", "Purpose");
    private static final String EVT = XsltChecks.EVT;

    private final HeldSchemas heldSchemas;

    /// @param heldSchemas The schemas this Stroom holds.
    @Inject
    public EventChecks(final HeldSchemas heldSchemas) {
        this.heldSchemas = heldSchemas;
    }

    /// Validates event XML against the event-logging XSD held in this Stroom, giving each error with its line,
    /// element path and message. A records:2 document (a Data Splitter's output, a Lucene indexing XSLT's) is
    /// validated against the records schema instead; any other XML (a source's own, say `<records>` with no
    /// namespace) is not Events, and is said to be so rather than failing every element.
    ///
    /// @param eventsXml     An `<Events>` document, e.g. step output.
    /// @param schemaVersion The event-logging version to validate against, e.g. `4.1.0`, or null for the version
    ///                      the document declares in xsi:schemaLocation or its Version, else the newest this
    ///                      Stroom holds.
    /// @return `valid`, `schema` (the system id, or null for XML that is not Events), `error_count` and `errors`
    /// (the first [HeldSchemas#MAX_ERRORS], each `line`, `path` and `message`).
    /// @throws ToolRefusal if it is not well-formed, or Stroom holds no such schema.
    public Map<String, Object> validateEvents(final String eventsXml, final String schemaVersion) {
        final Element root = XsltChecks.parse(eventsXml, "Events XML");
        final String namespace = root.getNamespaceURI();
        final String name = root.getLocalName();
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!HeldSchemas.EVENT_LOGGING_NS.equals(namespace) && !HeldSchemas.RECORDS_NS.equals(namespace)) {
            final Map<String, Object> error = new LinkedHashMap<>();
            error.put("line", null);
            error.put("path", "/" + name);
            error.put("message", "Not an Events document: its root is <" + name + "> in " + (namespace != null
                    ? "namespace " + namespace
                    : "no namespace") + ", neither event-logging:3 Events nor records:2 records. A source's own "
                                 + "XML (records of its own, say) is the input to a translation: validate what the "
                                 + "translation writes (step_pipeline's output).");
            result.put("valid", false);
            result.put("schema", null);
            result.put("error_count", 1);
            result.put("errors", List.of(error));
            return result;
        }
        final String systemId = systemId(eventsXml, root, schemaVersion);
        final List<SchemaError> errors;
        try {
            errors = heldSchemas.validate(systemId, eventsXml);
        } catch (final NotWellFormedException e) {
            throw new ToolRefusal("Events XML is not well-formed XML: " + e.getMessage());
        }
        result.put("valid", errors.isEmpty());
        result.put("schema", systemId);
        result.put("error_count", errors.size());
        result.put("errors", errors.stream()
                .limit(HeldSchemas.MAX_ERRORS)
                .map(SchemaError::toMap)
                .toList());
        return result;
    }

    /// Quality checks beyond the schema, per event: TimeCreated is a full UTC timestamp, System Name,
    /// Environment, Generator, Device and TypeId are present, EventDetail has exactly one action, and no element
    /// is empty.
    ///
    /// @param eventsXml An `<Events>` document (or one `<Event>`).
    /// @return `events_checked`, `ok`, `rules` (each rule failed, with `events_failing` and up to three
    /// `examples`), and `notes` when events have EventDetail/Unknown.
    /// @throws ToolRefusal if it is not well-formed.
    public static Map<String, Object> checkEventQuality(final String eventsXml) {
        final Element root = XsltChecks.parse(eventsXml, "Events XML");
        final List<Element> events = "Events".equals(root.getLocalName())
                ? XmlTrees.children(root).stream()
                        .filter(child -> EVT.equals(child.getNamespaceURI()) && "Event".equals(child.getLocalName()))
                        .toList()
                : List.of(root);
        final Map<String, Set<Integer>> failing = new TreeMap<>();
        final Map<String, List<Map<String, Object>>> examples = new TreeMap<>();
        final List<Integer> unknown = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            final Element event = events.get(index);
            for (final Finding finding : eventFindings(event)) {
                failing.computeIfAbsent(finding.rule(), k -> new LinkedHashSet<>()).add(index);
                final List<Map<String, Object>> shown = examples.computeIfAbsent(finding.rule(),
                        k -> new ArrayList<>());
                if (shown.size() < 3) {
                    final Map<String, Object> example = new LinkedHashMap<>();
                    example.put("event", index);
                    example.put("message", finding.message());
                    shown.add(example);
                }
            }
            if (find(event, "EventDetail", "Unknown") != null) {
                unknown.add(index);
            }
        }
        final Map<String, Object> rules = new LinkedHashMap<>();
        failing.forEach((rule, indexes) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("events_failing", indexes.size());
            entry.put("examples", examples.get(rule));
            rules.put(rule, entry);
        });
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("events_checked", events.size());
        result.put("ok", rules.isEmpty());
        result.put("rules", rules);
        if (!unknown.isEmpty()) {
            // Advice, not a failure: Unknown is right for records no action element describes.
            result.put("notes", List.of(unknown.size() + " of " + events.size() + " events (e.g. "
                                        + PyText.repr(unknown.subList(0, Math.min(3, unknown.size())))
                                        + ") have EventDetail/Unknown: what happened is not known. If those records "
                                        + "are an activity another action element describes (Alert, Authenticate, "
                                        + "Network, Process, Create, Update, Delete, View, ...), use it."));
        }
        return result;
    }

    /// @param event An Event element.
    /// @return Each quality rule it fails, with what is wrong.
    public static List<Finding> eventFindings(final Element event) {
        final List<Finding> found = new ArrayList<>();
        final String created = XmlTrees.text(find(event, "EventTime", "TimeCreated"));
        if (!ISO_TIME.matcher(created).matches()) {
            found.add(new Finding("time_created", "EventTime/TimeCreated is " + PyText.repr(created)
                                                  + ", not yyyy-MM-ddTHH:mm:ss.SSSZ"));
        }
        final Map<String, String[]> required = new LinkedHashMap<>();
        required.put("system_name", new String[]{"EventSource", "System", "Name"});
        required.put("system_environment", new String[]{"EventSource", "System", "Environment"});
        required.put("generator", new String[]{"EventSource", "Generator"});
        required.put("type_id", new String[]{"EventDetail", "TypeId"});
        required.forEach((rule, path) -> {
            if (XmlTrees.text(find(event, path)).isEmpty()) {
                found.add(new Finding(rule, String.join("/", path) + " is missing or empty"));
            }
        });
        if (find(event, "EventSource", "Device") == null) {
            found.add(new Finding("device", "EventSource/Device is missing"));
        }
        final Element detail = find(event, "EventDetail");
        final List<String> actions = detail == null
                ? List.of()
                : XmlTrees.children(detail).stream()
                        .map(Element::getLocalName)
                        .filter(name -> !DETAIL_META.contains(name))
                        .toList();
        if (actions.size() != 1) {
            found.add(new Finding("action", "EventDetail should hold exactly one action element, found "
                                            + (actions.isEmpty()
                    ? "none"
                    : PyText.list(actions))));
        }
        for (final Element node : XmlTrees.descendantsAndSelf(event)) {
            if (XmlTrees.isLeaf(node) && XmlTrees.text(node).isEmpty() && XmlTrees.attributes(node).isEmpty()) {
                found.add(new Finding("empty_element", path(node, event.getParentNode()) + " is empty"));
            }
        }
        return found;
    }

    // The system id to validate against.
    private String systemId(final String eventsXml, final Element root, final String schemaVersion) {
        final String declared = HeldSchemas.declaredSystemId(eventsXml);
        if (HeldSchemas.RECORDS_NS.equals(root.getNamespaceURI())) {
            // Records are records: their own schema, whatever version they declare, else the one Stroom ships.
            return declared != null && declared.contains("records")
                    ? declared
                    : HeldSchemas.RECORDS_SYSTEM_ID;
        }
        if (schemaVersion != null && !schemaVersion.isBlank()) {
            return HeldSchemas.eventLoggingSystemId(schemaVersion);
        }
        if (declared != null) {
            return declared;
        }
        final String version = root.getAttribute("Version");
        return HeldSchemas.eventLoggingSystemId(version.isEmpty()
                ? heldSchemas.eventVersions().getFirst()
                : version);
    }

    // The element at a path of event-logging children below node, or null.
    private static Element find(final Element node, final String... path) {
        Element current = node;
        for (final String step : path) {
            current = XmlTrees.children(current).stream()
                    .filter(child -> EVT.equals(child.getNamespaceURI()) && step.equals(child.getLocalName()))
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String path(final Element node, final Node stop) {
        final List<String> parts = new ArrayList<>();
        Node current = node;
        while (current instanceof final Element element && current != stop) {
            parts.addFirst(element.getLocalName());
            current = element.getParentNode();
        }
        return String.join("/", parts);
    }


    // --------------------------------------------------------------------------------


    /// A quality rule an event fails.
    ///
    /// @param rule    The rule, e.g. `time_created`.
    /// @param message What is wrong.
    public record Finding(String rule, String message) {

    }
}
