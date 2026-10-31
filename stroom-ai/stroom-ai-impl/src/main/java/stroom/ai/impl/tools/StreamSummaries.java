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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.ErrorTriageConfig;
import stroom.ai.impl.tools.StreamReader.RawText;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StoredError;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/// Summaries of streams, as the user: the error markers of Error streams (or the Error children of raw or Events
/// streams), grouped and triaged as blocking, review or benign (see [ErrorTriage]); and what Events streams hold,
/// by EventDetail type, TypeId and Action, and how often each event-logging path is populated.
///
/// Callers check the user may view data first. Streams are found through [MetaService] and read through the
/// stream store, which only reach streams of feeds the user may view.
public class StreamSummaries {

    /// The verdict of an error summary that couldn't read every Error stream and found nothing in the rest.
    public static final String UNREAD = "unread";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamSummaries.class);

    /// The event-logging schema's namespace.
    public static final String EVT = "event-logging:3";
    /// The key of how often each path (and named Data entry, `<path>[@Name='<name>']/@Value`) is populated, as a
    /// share of all events, by the action element of the events that have it: for the tools' own use, not for
    /// summarise_events' reply.
    public static final String POPULATION_BY_ACTION = "path_population_by_action";
    /// The key of each TypeId's share of all events (%), by the action element of its events: for the tools' own
    /// use, not for summarise_events' reply.
    public static final String TYPE_IDS_BY_ACTION = "type_ids_by_action";
    /// The key of each path's values in the events read (a Data entry's as `<path>[@Name='<name>']/@Value`), up to
    /// [#MAX_VALUES_PER_PATH] each: what the indexing tools look in for stand-ins for none (`-`). Not in the Stroom
    /// MCP server.
    public static final String VALUES_BY_PATH = "values_by_path";
    /// The most values kept of a path.
    static final int MAX_VALUES_PER_PATH = 500;
    /// The most Error children of a stream summarised.
    static final int MAX_ERROR_STREAMS = 20;
    /// The most markers read from one Error stream.
    static final int MAX_MARKERS_PER_STREAM = 1_000;
    /// The most bytes of one Error stream read.
    static final int MAX_ERROR_STREAM_BYTES = 4 * 1024 * 1024;
    /// The character budget for reading Events records: in effect, none.
    static final int EVENTS_BUDGET = 50_000_000;
    static final int MAX_COUNTED = 50;

    private static final Set<String> DETAIL_META = Set.of("TypeId", "Description", "Classification", "Purpose");

    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;
    private final AcceptedErrors acceptedErrors;
    private final Provider<AiConfig> aiConfigProvider;

    /// @param streamReader     Reads the streams.
    /// @param documentAccess   Reads pipelines' documents, as the user.
    /// @param acceptedErrors   The errors each pipeline's builds accepted.
    /// @param aiConfigProvider The environment's error triage rules.
    @Inject
    public StreamSummaries(final StreamReader streamReader,
                           final DocumentAccess documentAccess,
                           final AcceptedErrors acceptedErrors,
                           final Provider<AiConfig> aiConfigProvider) {
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
        this.acceptedErrors = acceptedErrors;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @return Error triage by the environment's rules and then Stroom's own.
    /// @throws IllegalArgumentException if one of the environment's rules can't be read.
    public ErrorTriage errorTriage() {
        return ErrorTriage.withEnvironmentRules(NullSafe.getOrElse(aiConfigProvider.get(),
                AiConfig::getErrorTriage, ErrorTriageConfig::getRules, List.of()));
    }

    // --------------------------------------------------------------------------------
    // Errors.
    // --------------------------------------------------------------------------------

    /// Summarises error markers: grouped by severity, element and message, each group classified as blocking
    /// (must fix), review (may be caused by the pipeline's own content) or benign (report only). Elements the
    /// pipeline sets itself are distinguished from ones inherited from its template.
    ///
    /// @param streamId An Error stream, or a Raw Events/Events stream whose Error children to summarise.
    /// @return `stream_id`, `error_streams`, the triage (`verdict`, `groups_by_class`, `groups`) and any `unread`,
    /// as the Stroom MCP server gave them.
    /// @throws StreamReadException if there is no such stream.
    public Map<String, Object> summariseErrors(final long streamId) {
        final ErrorTriage triage = errorTriage();
        final Meta meta = streamReader.meta(streamId);
        final List<Meta> errorStreams;
        if (StreamTypeNames.ERROR.equals(meta.getTypeName())) {
            errorStreams = List.of(meta);
        } else {
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, streamId)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                    .build();
            errorStreams = NullSafe.list(NullSafe.get(streamReader.find(expression, MAX_ERROR_STREAMS, true),
                            ResultPage::getValues)).stream()
                    .map(MetaRow::getMeta)
                    .toList();
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("stream_id", streamId);
        if (errorStreams.isEmpty()) {
            result.put("error_streams", List.of());
            result.putAll(triage.triage(List.of(), Set.of()));
            return result;
        }

        final List<ErrorTriage.Marker> markers = new ArrayList<>();
        final Set<String> own = new HashSet<>();
        final List<AcceptedErrors.Entry> accepted = new ArrayList<>();
        final List<String> unread = new ArrayList<>();
        final Set<String> pipelinesRead = new HashSet<>();
        for (final Meta errorStream : errorStreams) {
            // Production streams: an Error stream Stroom can't read (its volume gone), or written by a pipeline it
            // can no longer load (deleted, or a property its element type lost), is said; the rest are still
            // summarised.
            try {
                markers.addAll(markers(errorStream.getId()));
            } catch (final IOException | RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to read Error stream {}", errorStream.getId()), e);
                unread.add("Error stream " + errorStream.getId() + ": " + reason(e));
                continue;
            }
            final String pipelineUuid = errorStream.getPipelineUuid();
            if (pipelineUuid != null && pipelinesRead.add(pipelineUuid)) {
                // Its elements unknown: none of its errors is told apart as the pipeline's own.
                MergedPipeline.load(documentAccess, pipelineUuid)
                        .ifPresent(layers -> own.addAll(layers.ownElements()));
                accepted.addAll(acceptedErrors.acceptedFor(pipelineUuid));
            }
        }
        result.put("error_streams", errorStreams.stream().map(Meta::getId).toList());
        result.putAll(triage.triage(markers, own, null, 3, accepted));
        if (!unread.isEmpty()) {
            result.put("unread", unread);
            if ("clean".equals(result.get("verdict"))) {
                // Not clean: nothing is known of what it couldn't read (seen in testing: an Error stream still
                // being written, unreadable until its task ended, summarised as clean).
                result.put("verdict", UNREAD);
                result.put("hint", "Not every Error stream could be read (unread), so this doesn't say they are "
                                   + "clean. One still being written (its processing task running) can be read once "
                                   + "the task has finished: wait_for_processing, then summarise them again.");
            }
        }
        return result;
    }

    private List<ErrorTriage.Marker> markers(final long errorStreamId) throws IOException {
        return errorMarkers(errorStreamId, MAX_MARKERS_PER_STREAM);
    }

    /// The markers an Error stream holds, read as the user (as the data viewer's marker mode gives them).
    ///
    /// @param errorStreamId An Error stream.
    /// @param max           The most markers to give.
    /// @return Its first markers, up to `max`.
    /// @throws IOException      if it can't be read.
    /// @throws RuntimeException if it can't be opened, e.g. it has been deleted or the user may not view its feed.
    public List<ErrorTriage.Marker> errorMarkers(final long errorStreamId, final int max) throws IOException {
        final RawText text = streamReader.errorStreamText(errorStreamId,
                MAX_ERROR_STREAM_BYTES);
        final List<StoredError> stored = ErrorStreamMarkers.parse(text.text(), text.truncated()).markers();
        return stored.subList(0, Math.min(stored.size(), max)).stream()
                .map(ErrorTriage::fromStoredError)
                .toList();
    }

    /// @param e A failure.
    /// @return Its reason, for the agent.
    public static String reason(final Exception e) {
        final String message = NullSafe.isBlankString(e.getMessage())
                ? e.getClass().getSimpleName()
                : e.getMessage().strip();
        return message.length() > 300
                ? message.substring(0, 300)
                : message;
    }

    // --------------------------------------------------------------------------------
    // Events.
    // --------------------------------------------------------------------------------

    /// Profiles Events streams: how many events of each EventDetail type, TypeId and Action there are, and how
    /// often each event-logging path is populated.
    ///
    /// @param streamIds The Events streams.
    /// @param maxEvents The most events to read.
    /// @return `events`, `event_types`, `type_ids`, `actions`, `path_population`, `data_names` and `hint`, as the
    /// Stroom MCP server gave them, and [#POPULATION_BY_ACTION] and [#TYPE_IDS_BY_ACTION].
    /// @throws StreamReadException if a stream can't be read.
    public Map<String, Object> summariseEvents(final List<Long> streamIds, final int maxEvents) {
        final Map<String, Integer> types = new LinkedHashMap<>();
        final Map<String, Integer> typeIds = new LinkedHashMap<>();
        final Map<String, Integer> actions = new LinkedHashMap<>();
        final Map<String, Integer> populated = new TreeMap<>();
        final Map<List<String>, Integer> dataNames = new LinkedHashMap<>();
        // Each path (and named Data entry) by the action element of the events that have it.
        final Map<String, Map<String, Integer>> byAction = new TreeMap<>();
        final Map<String, Map<String, Integer>> typeIdsByAction = new TreeMap<>();
        final Map<String, List<String>> valuesByPath = new TreeMap<>();
        int count = 0;
        final DocumentBuilder builder = documentBuilder();
        for (final long streamId : streamIds) {
            final List<String> records = streamReader.readRecords(streamId, 0, maxEvents - count, null,
                    EVENTS_BUDGET).records();
            for (final String record : records) {
                final Document document;
                try {
                    document = builder.parse(new InputSource(new StringReader(record)));
                } catch (final Exception e) {
                    // One record that isn't XML (a truncated or hand-edited stream): passed over.
                    continue;
                }
                final NodeList events = document.getElementsByTagNameNS(EVT, "Event");
                for (int i = 0; i < events.getLength(); i++) {
                    final Element event = (Element) events.item(i);
                    count++;
                    final Element detail = child(event, "EventDetail");
                    final Element action = detail == null
                            ? null
                            : childElements(detail).stream()
                                    .filter(c -> !DETAIL_META.contains(c.getLocalName()))
                                    .findFirst()
                                    .orElse(null);
                    final String kind = action == null
                            ? "(none)"
                            : action.getLocalName();
                    types.merge(kind, 1, Integer::sum);
                    final Element typeId = detail == null
                            ? null
                            : child(detail, "TypeId");
                    typeIds.merge(orNone(typeId), 1, Integer::sum);
                    if (action != null) {
                        actions.merge(kind + "/" + orNone(child(action, "Action")), 1, Integer::sum);
                    }
                    final Map<String, Integer> ofAction = action == null
                            ? null
                            : byAction.computeIfAbsent(kind, k -> new TreeMap<>());
                    if (action != null) {
                        typeIdsByAction.computeIfAbsent(kind, k -> new LinkedHashMap<>())
                                .merge(orNone(typeId), 1, Integer::sum);
                    }
                    leafPaths(event).forEach(path -> {
                        populated.merge(path, 1, Integer::sum);
                        if (ofAction != null) {
                            ofAction.merge(path, 1, Integer::sum);
                        }
                    });
                    values(event, valuesByPath);
                    dataNames(event).forEach(name -> {
                        dataNames.merge(name, 1, Integer::sum);
                        if (ofAction != null) {
                            ofAction.merge(name.get(0) + "[@Name='" + name.get(1) + "']/@Value", 1, Integer::sum);
                        }
                    });
                }
            }
            if (count >= maxEvents) {
                break;
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", count);
        result.put("event_types", mostCommon(types, Integer.MAX_VALUE));
        result.put("type_ids", mostCommon(typeIds, MAX_COUNTED));
        result.put("actions", mostCommon(actions, MAX_COUNTED));
        final Map<String, Double> population = new LinkedHashMap<>();
        final Map<String, Map<String, Double>> names = new LinkedHashMap<>();
        if (count > 0) {
            final int events = count;
            populated.forEach((path, n) -> population.put(path, percent(n, events)));
            // Which Data elements each Data path holds, by Name (each as a percentage of events): the path alone
            // doesn't say, and an index plan maps them one by one.
            final Map<String, Map<String, Integer>> byPath = new TreeMap<>();
            dataNames.forEach((key, n) -> byPath.computeIfAbsent(key.get(0), k -> new TreeMap<>())
                    .put(key.get(1), n));
            byPath.forEach((path, byName) -> {
                final Map<String, Double> shares = new LinkedHashMap<>();
                byName.forEach((name, n) -> shares.put(name, percent(n, events)));
                names.put(path, shares);
            });
        }
        result.put("path_population", population);
        result.put("data_names", names);
        // Not in the Stroom MCP server: for documenting a field only some kinds of event have under each of them
        // (seen: source.ip, from EventSource, in 40% of the events, which could be put under no event type).
        final Map<String, Map<String, Double>> actionShares = new LinkedHashMap<>();
        if (count > 0) {
            final int events = count;
            byAction.forEach((action, paths) -> {
                final Map<String, Double> shares = new LinkedHashMap<>();
                paths.forEach((path, n) -> shares.put(path, percent(n, events)));
                actionShares.put(action, shares);
            });
        }
        result.put(POPULATION_BY_ACTION, actionShares);
        // Which TypeIds each action element's events have, most first (seen: an index's section per action element
        // said nothing of which kinds of event, by TypeId, it was).
        final Map<String, Map<String, Double>> typeIdShares = new LinkedHashMap<>();
        if (count > 0) {
            final int events = count;
            typeIdsByAction.forEach((action, ids) -> {
                final Map<String, Double> shares = new LinkedHashMap<>();
                ids.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> shares.put(entry.getKey(), percent(entry.getValue(), events)));
                typeIdShares.put(action, shares);
            });
        }
        result.put(TYPE_IDS_BY_ACTION, typeIdShares);
        result.put(VALUES_BY_PATH, valuesByPath);
        result.put("hint", count >= maxEvents
                ? "Percentages are of events sampled; raise max_events for a fuller picture."
                : null);
        return result;
    }

    private static double percent(final int n, final int of) {
        return Math.round(1000.0 * n / of) / 10.0;
    }

    private static Map<String, Integer> mostCommon(final Map<String, Integer> counts, final int limit) {
        final List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        // Stable: equal counts keep the order they were first seen in.
        entries.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        final Map<String, Integer> result = new LinkedHashMap<>();
        entries.stream().limit(limit).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static String orNone(final Element element) {
        final String text = element == null
                ? ""
                : element.getTextContent();
        return NullSafe.isBlankString(text)
                ? "(none)"
                : text.strip();
    }

    // Paths (from Event) of elements that carry a value or attributes, ignoring repeats.
    private static Set<String> leafPaths(final Element event) {
        final Set<String> paths = new LinkedHashSet<>();
        final NodeList all = event.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            final Element node = (Element) all.item(i);
            if (!leadingText(node).isBlank() || node.getAttributes().getLength() > 0) {
                paths.add(path(node, event));
            }
        }
        return paths;
    }

    // Each element's text and each Data entry's Value in the event, added to its path's values (up to
    // MAX_VALUES_PER_PATH).
    private static void values(final Element event, final Map<String, List<String>> valuesByPath) {
        final NodeList all = event.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            final Element node = (Element) all.item(i);
            final String text = leadingText(node);
            if (!text.isBlank()) {
                addValue(valuesByPath, path(node, event), text.strip());
            }
            if (EVT.equals(node.getNamespaceURI()) && "Data".equals(node.getLocalName())
                && NullSafe.isNonEmptyString(node.getAttribute("Name")) && node.hasAttribute("Value")) {
                addValue(valuesByPath, path(node, event) + "[@Name='" + node.getAttribute("Name") + "']/@Value",
                        node.getAttribute("Value").strip());
            }
        }
    }

    private static void addValue(final Map<String, List<String>> valuesByPath, final String path, final String value) {
        final List<String> values = valuesByPath.computeIfAbsent(path, key -> new ArrayList<>());
        if (values.size() < MAX_VALUES_PER_PATH) {
            values.add(value);
        }
    }

    // (path of the Data element, its Name) for each Data element with a value, ignoring repeats.
    private static Set<List<String>> dataNames(final Element event) {
        final Set<List<String>> names = new LinkedHashSet<>();
        final NodeList data = event.getElementsByTagNameNS(EVT, "Data");
        for (int i = 0; i < data.getLength(); i++) {
            final Element element = (Element) data.item(i);
            if (NullSafe.isNonEmptyString(element.getAttribute("Name"))
                && !element.getAttribute("Value").isBlank()) {
                names.add(List.of(path(element, event), element.getAttribute("Name")));
            }
        }
        return names;
    }

    private static String path(final Element node, final Element event) {
        final List<String> parts = new ArrayList<>();
        Node current = node;
        while (current != null && current != event) {
            parts.addFirst(localName(current));
            current = current.getParentNode();
        }
        return String.join("/", parts);
    }

    private static String localName(final Node node) {
        return node.getLocalName() == null
                ? node.getNodeName()
                : node.getLocalName();
    }

    // The text before an element's first child element, as lxml's text is.
    private static String leadingText(final Element element) {
        final StringBuilder sb = new StringBuilder();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                break;
            }
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        return sb.toString();
    }

    private static Element child(final Element parent, final String localName) {
        return childElements(parent).stream()
                .filter(c -> localName.equals(c.getLocalName()) && EVT.equals(c.getNamespaceURI()))
                .findFirst()
                .orElse(null);
    }

    private static List<Element> childElements(final Element parent) {
        final List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof final Element element) {
                children.add(element);
            }
        }
        return children;
    }

    /// @return A namespace-aware parser that reads no DTDs or external entities.
    public static DocumentBuilder documentBuilder() {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            // Errors are failures to parse, not messages on standard error.
            builder.setErrorHandler(null);
            return builder;
        } catch (final ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }
}
