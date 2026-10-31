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

import stroom.ai.impl.tools.IndexEvents.Find;
import stroom.ai.impl.tools.IndexEvents.Term;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.SafeJq;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.thisptr.jackson.jq.JsonQuery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Searches each event of a file written by fetch_events (see [EventsFile]): gets values from each by XPath, and/or
/// finds text in each, and writes a table of the results keyed by StreamId and EventId, optionally added as a
/// column to the table the events came from, so they can be analysed with the table tool.
///
/// Or, given an index, finds events in it through Stroom as the user would on a dashboard ([IndexEvents]): for an
/// event the user describes but can't point at.
class SearchEventsTool extends AbstractWorkspaceTool {

    static final String NAME = "search_events";
    static final String XPATH_ARG = "xpath";
    static final String NAMESPACES_ARG = "namespaces";
    static final String TEXT_ARG = "text";
    static final String REGEX_ARG = "regex";
    static final String IGNORE_CASE_ARG = "ignoreCase";
    static final String COLUMN_ARG = "column";
    static final String JOIN_ARG = "join";
    static final String JQ_ARG = "jq";
    static final String INDEX_ARG = "index";
    static final String WHERE_ARG = "where";
    static final String TIME_FROM_ARG = "timeFrom";
    static final String TIME_TO_ARG = "timeTo";
    static final String FIELDS_ARG = "fields";
    static final String LIMIT_ARG = "limit";
    static final String ALL_STREAMS_ARG = "allStreams";
    static final String DEFAULT_OUT = "/outputs/search_events.csv";
    static final String DEFAULT_PREFIX = "d";
    static final int PREVIEW_ROWS = 10;
    // The most of a value or snippet kept for one event.
    static final int MAX_VALUE_CHARS = 1000;
    static final int SNIPPET_CHARS_BEFORE = 60;
    static final int SNIPPET_CHARS = 200;
    static final String VALUE_SEPARATOR = " | ";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WHERE_SHAPE = "where is a list of {\"field\": ..., \"condition\": ..., "
                                              + "\"value\": ...}";

    private final IndexEvents indexEvents;

    @Inject
    SearchEventsTool(final ChatWorkspace workspace, final IndexEvents indexEvents) {
        super(workspace);
        this.indexEvents = indexEvents;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("With index: Use on the index is checked, as for any search; the raw streams allStreams "
                      + "gives are only those of feeds the user may view.")
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It runs a search.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Searches each event in a file")
                        .chatOnly(" from fetch_events")
                        .general(", writing a table with one row per event: StreamId, EventId and the result. Give "
                                 + "xpath to get a value from each event (evaluated against that event alone, e.g. "
                                 + "'//d:EventDetail/d:Authenticate/d:Outcome/d:Reason'; the events' namespace is "
                                 + "bound to '" + DEFAULT_PREFIX + "'; several values are joined with ' | '). Give "
                                 + "text to keep only the "
                                 + "events containing it (in the xpath's value if given, else anywhere in the "
                                 + "event), with a snippet around the match. Give join (the table the events came "
                                 + "from) to add the result as a column to that table instead. Give jq with xpath to "
                                 + "parse each XPath value as JSON and apply a jq filter to it, with a row per "
                                 + "result (e.g. xpath /d:map/d:string[@key='message'] with jq '.. | .username? // "
                                 + "empty' for every username at any depth); values that aren't JSON are counted, "
                                 + "not failed.")
                        .chatOnly(" Then use the table tool on the output to count, group or filter.")
                        .general(" Or give index instead to find events in an index through Stroom, as the user "
                                 + "would on a dashboard (with their permissions; nothing is saved): for an event "
                                 + "the user describes but can't point at. Each hit has its Events stream's "
                                 + "StreamId and its EventId, for locate_event, which traces it to its raw record; "
                                 + "with how many events match in all, and in which streams. Searches only fields "
                                 + "the index has, and refuses a search whose answer through Stroom would mislead, "
                                 + "saying what to search instead.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The events file")
                                .chatOnly(", e.g. /outputs/events.xml")
                                .general(".")
                                .build())
                        .addStringProperty(INDEX_ARG, ToolDescription.builder()
                                .general("The index to search, in place of a file: its Elastic Index or Lucene "
                                         + "Index doc's uuid or name")
                                .chatOnly(", as list_data_sources gives it")
                                .general(".")
                                .build())
                        .addProperty(WHERE_ARG, ArraySchema.builder()
                                .items(ObjectSchema.builder()
                                        .description("One condition, as a person would set it on a dashboard.")
                                        .addStringProperty("field", "The index's field.")
                                        .addEnumProperty("condition", IndexEvents.CONDITIONS.stream()
                                                .map(Condition::name)
                                                .toList(), "The condition (default EQUALS).")
                                        .addStringProperty("value", "BETWEEN: 'from,to'; IN: values separated by "
                                                                    + "commas; EQUALS takes * wildcards.")
                                        .required("field")
                                        .build())
                                .description("With index: conditions the event meets, all of them, on the index's "
                                             + "fields as the user describes it, e.g. [{\"field\": \"TypeId\", "
                                             + "\"value\": \"RoleChange\"}, {\"field\": \"UserId\", "
                                             + "\"value\": \"admin*\"}].")
                                .build())
                        .addStringProperty(TIME_FROM_ARG, "With index: from this time (ISO 8601, or Stroom's "
                                                          + "day()-7d).")
                        .addStringProperty(TIME_TO_ARG, "With index: up to this time (ISO 8601, or Stroom's now()).")
                        .addProperty(FIELDS_ARG, stringArraySchema("With index: index fields to show for each hit "
                                                                   + "beside StreamId, EventId and the time (the "
                                                                   + "searched ones are shown too)."))
                        .addIntegerProperty(LIMIT_ARG, "With index: hits to return, newest first (default "
                                                       + IndexEvents.DEFAULT_HITS + ", at most "
                                                       + IndexEvents.MAX_HITS + ").")
                        .addBooleanProperty(ALL_STREAMS_ARG, "With index: every Events stream holding a match (not "
                                                             + "only the " + IndexEvents.STREAMS + " with the most), "
                                                             + "with the raw stream each was made from: the streams "
                                                             + "to reprocess once a translation change is promoted, "
                                                             + "and for Elasticsearch the request that deletes their "
                                                             + "old documents first.")
                        .addStringProperty(XPATH_ARG, "An XPath expression to evaluate against each event.")
                        .addProperty(NAMESPACES_ARG, ObjectSchema.builder()
                                .description("Namespace prefixes to use, e.g. {\"e\": \"event-logging:3\"}.")
                                .build())
                        .addStringProperty(JQ_ARG, "A jq filter to apply to each XPath value, parsed as "
                                                   + "JSON (needs xpath).")
                        .addStringProperty(TEXT_ARG, "Text to find in each event.")
                        .addBooleanProperty(REGEX_ARG, "True if text is a regular expression.")
                        .addBooleanProperty(IGNORE_CASE_ARG, "False to match the case of text (default true).")
                        .addStringProperty(COLUMN_ARG, "A name for the result column (default 'value' with "
                                                       + "xpath, else 'match').")
                        .addStringProperty(JOIN_ARG, ToolDescription.builder()
                                .general("A table with StreamId and EventId columns to add the result column to")
                                .chatOnly(", e.g. /attachments/12.md")
                                .general(".")
                                .build())
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .csv file in /outputs/ to write the table to (default " + DEFAULT_OUT
                                          + ").")
                                .build())
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCall request, final ToolCallContext context) {
        // A search of an index takes as long as a query may.
        return stringArg(request, INDEX_ARG) != null
                ? limits(context).getQueryTimeLimit().getDuration()
                : timeLimit(context);
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        if (stringArg(request, INDEX_ARG) != null) {
            return findInIndex(request, context);
        }
        if (stringArg(request, PATH_ARG) == null) {
            throw new WorkspaceException("Give path (an events file), or index to find events in an index.");
        }
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final WorkspacePath requestedOut = outArg(request);
        final WorkspacePath out = requestedOut != null
                ? requestedOut
                : WorkspacePath.parse(DEFAULT_OUT);
        workspace.checkWritable(out);
        final String expression = rawArg(request, XPATH_ARG);
        final String text = rawArg(request, TEXT_ARG);
        final boolean hasXPath = expression != null && !expression.isBlank();
        final boolean hasText = text != null && !text.isEmpty();
        if (!hasXPath && !hasText) {
            throw new WorkspaceException("Give xpath, text, or both.");
        }
        final SafePattern pattern = hasText
                ? SafePattern.of(text, booleanArg(request, REGEX_ARG, false),
                booleanArg(request, IGNORE_CASE_ARG, true))
                : null;
        final String column = nonBlankElse(rawArg(request, COLUMN_ARG), hasXPath
                ? "value"
                : "match");
        final String jq = rawArg(request, JQ_ARG);
        final boolean hasJq = jq != null && !jq.isBlank();
        if (hasJq && !hasXPath) {
            throw new WorkspaceException("Give xpath with jq: jq is applied to each event's XPath value.");
        }
        final JsonQuery query = hasJq
                ? SafeJq.compile(jq)
                : null;

        final List<XdmNode> events = events(SafeXPath.parse(workspace.localFile(path)));
        final XPathExecutable executable = hasXPath
                ? SafeXPath.compile(expression, namespaces(request, events))
                : null;

        // The results for each event that has any, keyed by its stream and event ids.
        final Map<List<String>, List<String>> results = new LinkedHashMap<>();
        final JqErrors jqErrors = new JqErrors();
        for (final XdmNode event : events) {
            ToolDeadline.check();
            final XdmNode content = content(event);
            final List<String> key = List.of(event.attribute(EventsFile.STREAM_ID_ATTRIBUTE),
                    event.attribute(EventsFile.EVENT_ID_ATTRIBUTE));
            if (query != null) {
                final List<String> outputs = content != null
                        ? jqValues(query, xpathValues(executable, content), pattern, jqErrors)
                        : List.of();
                if (!outputs.isEmpty()) {
                    results.put(key, outputs);
                }
                continue;
            }
            final String value = executable != null
                    ? (content != null
                    ? xpathValue(executable, content)
                    : "")
                    : null;
            final String result;
            if (pattern != null) {
                final String haystack = value != null
                        ? value
                        : (content != null
                                ? content.getStringValue()
                                : event.getStringValue());
                final int at = pattern.indexIn(haystack);
                if (at < 0) {
                    continue;
                }
                result = value != null
                        ? value
                        : snippet(haystack, at);
            } else {
                result = value;
            }
            results.put(key, List.of(result));
        }

        final String joinArg = rawArg(request, JOIN_ARG);
        final Result table = joinArg != null && !joinArg.isBlank()
                ? join(workspace, WorkspacePath.parse(joinArg), column, results)
                : asTable(column, results);
        writeCsv(workspace, out, table, workspace.sourceDocUuid(path));
        final String found = pattern != null || query != null
                ? results.size() + " of " + events.size() + " event(s) " + (query != null
                ? "gave a jq result"
                : "matched")
                : "Searched " + events.size() + " event(s)";
        return ToolResult.text(found + jqErrors.summary() + ". Wrote " + table.rows().size() + " row(s) to " + out
                               + "; use the table tool on it to "
                               + "count, group or filter. The first rows:\n\n" + markdown(table));
    }

    // Finds events in an index.
    private ToolResult findInIndex(final ToolCall request, final ToolCallContext context) {
        final Integer limit = intArg(request, LIMIT_ARG);
        final Find find = new Find(stringArg(request, INDEX_ARG), terms(request), stringArg(request, TIME_FROM_ARG),
                stringArg(request, TIME_TO_ARG), stringListArg(request, FIELDS_ARG),
                Objects.requireNonNullElse(limit, IndexEvents.DEFAULT_HITS), booleanArg(request, ALL_STREAMS_ARG,
                false));
        final Map<String, Object> result = indexEvents.find(context, find);
        return ToolResult.text(JsonUtil.writeValueAsString(result, false)).withStructuredContent(result);
    }

    /// @param request The model's call.
    /// @return Its where conditions: a list of `{field, condition, value}`, or one.
    static List<Term> terms(final ToolCall request) {
        final Object given = argsMap(request).get(WHERE_ARG);
        final List<?> items;
        if (given == null) {
            items = List.of();
        } else if (given instanceof final List<?> list) {
            items = list;
        } else if (given instanceof final Map<?, ?> one) {
            items = List.of(one);
        } else {
            items = parsedList(String.valueOf(given));
        }
        final List<Term> terms = new ArrayList<>();
        for (final Object item : items) {
            if (!(item instanceof final Map<?, ?> term) || term.get("field") == null
                || String.valueOf(term.get("field")).isBlank()) {
                throw new WorkspaceException(WHERE_SHAPE + ", each with a field.");
            }
            terms.add(new Term(String.valueOf(term.get("field")).strip(), condition(term.get("condition")),
                    term.get("value") == null
                            ? ""
                            : String.valueOf(term.get("value"))));
        }
        return terms;
    }

    private static List<?> parsedList(final String text) {
        if (text.isBlank()) {
            return List.of();
        }
        try {
            final Object parsed = JsonUtil.readValue(text, Object.class);
            if (parsed instanceof final List<?> list) {
                return list;
            } else if (parsed instanceof final Map<?, ?> one) {
                return List.of(one);
            }
        } catch (final RuntimeException e) {
            // Not JSON: refused below.
        }
        throw new WorkspaceException(WHERE_SHAPE + ".");
    }

    private static Condition condition(final Object given) {
        if (given == null || String.valueOf(given).isBlank()) {
            return Condition.EQUALS;
        }
        final String name = String.valueOf(given).strip().toUpperCase(Locale.ROOT);
        for (final Condition condition : IndexEvents.CONDITIONS) {
            if (condition.name().equals(name)) {
                return condition;
            }
        }
        throw new WorkspaceException("Condition '" + given + "' isn't one of " + String.join(", ",
                IndexEvents.CONDITIONS.stream().map(Condition::name).toList()) + ".");
    }

    /// @return The events in the file, in order.
    static List<XdmNode> events(final XdmNode document) {
        final List<XdmNode> events = new ArrayList<>();
        for (final XdmNode root : document.children()) {
            if (root.getNodeKind() == XdmNodeKind.ELEMENT) {
                if (!EventsFile.ROOT_ELEMENT.equals(root.getNodeName().getLocalName())) {
                    throw new WorkspaceException("This isn't a file of events from fetch_events.");
                }
                for (final XdmNode child : root.children()) {
                    if (child.getNodeKind() == XdmNodeKind.ELEMENT
                        && EventsFile.EVENT_ELEMENT.equals(child.getNodeName().getLocalName())) {
                        events.add(child);
                    }
                }
            }
        }
        return events;
    }

    /// @return The event's XML (its root element), or null if the event is text.
    private static XdmNode content(final XdmNode event) {
        for (final XdmNode child : event.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        return null;
    }

    private static String xpathValue(final XPathExecutable executable, final XdmNode content) {
        final List<String> values = xpathValues(executable, content);
        return shorten(String.join(VALUE_SEPARATOR, values).replaceAll("\\s+", " ").strip(), MAX_VALUE_CHARS);
    }

    /// @return The values the expression gives for one event, as they are.
    private static List<String> xpathValues(final XPathExecutable executable, final XdmNode content) {
        final List<String> values = new ArrayList<>();
        // A document of its own, so the expression only sees this event.
        SafeXPath.evaluate(executable, SafeXPath.asDocument(content), false, values::add);
        return values;
    }

    /// Applies a jq filter to each of an event's XPath values, parsed as JSON.
    ///
    /// @param query   The jq filter.
    /// @param values  The event's XPath values.
    /// @param pattern Text the results must contain, or null.
    /// @param errors  Counts the values that aren't JSON, or that the filter fails on.
    /// @return The filter's results, as text.
    static List<String> jqValues(final JsonQuery query,
                                 final List<String> values,
                                 final SafePattern pattern,
                                 final JqErrors errors) {
        final List<String> outputs = new ArrayList<>();
        for (final String value : values) {
            final JsonNode json;
            try {
                json = MAPPER.readTree(value);
            } catch (final JsonProcessingException e) {
                errors.notJson++;
                continue;
            }
            if (json == null || json.isMissingNode()) {
                errors.notJson++;
                continue;
            }
            try {
                for (final JsonNode output : SafeJq.run(query, json)) {
                    final String text = output.isTextual()
                            ? output.asText()
                            : output.toString();
                    if (pattern == null || pattern.find(text)) {
                        outputs.add(shorten(text, MAX_VALUE_CHARS));
                    }
                }
            } catch (final ToolTimeoutException e) {
                throw e;
            } catch (final WorkspaceException e) {
                errors.failed++;
                if (errors.firstFailure == null) {
                    errors.firstFailure = e.getMessage();
                }
            }
        }
        return outputs;
    }

    /// @return The namespaces given, with the events' own namespace bound to [#DEFAULT_PREFIX] unless it is given.
    private static Map<String, String> namespaces(final ToolCall request, final List<XdmNode> events) {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        try {
            final Map<?, ?> args = JsonUtil.readValue(request.arguments(), Map.class);
            if (args != null && args.get(NAMESPACES_ARG) instanceof final Map<?, ?> given) {
                given.forEach((prefix, uri) -> namespaces.put(String.valueOf(prefix), String.valueOf(uri)));
            }
        } catch (final RuntimeException e) {
            // No namespaces given.
        }
        for (final XdmNode event : events) {
            final XdmNode content = content(event);
            if (content != null) {
                final String uri = content.getNodeName().getNamespaceURI();
                if (uri != null && !uri.isEmpty() && !namespaces.containsValue(uri)) {
                    namespaces.putIfAbsent(DEFAULT_PREFIX, uri);
                }
                break;
            }
        }
        return namespaces;
    }

    /// @return Part of the text around a match, on one line.
    static String snippet(final String text, final int at) {
        final int start = Math.max(0, at - SNIPPET_CHARS_BEFORE);
        final int end = Math.min(text.length(), start + SNIPPET_CHARS);
        return ((start > 0
                ? "…"
                : "") + text.substring(start, end) + (end < text.length()
                ? "…"
                : "")).replaceAll("\\s+", " ").strip();
    }

    private static String shorten(final String text, final int max) {
        return text.length() > max
                ? text.substring(0, max) + "…"
                : text;
    }

    private static Result asTable(final String column, final Map<List<String>, List<String>> results) {
        final List<List<String>> rows = new ArrayList<>();
        results.forEach((key, values) -> values.forEach(value ->
                rows.add(List.of(key.get(0), key.get(1), value))));
        return new Result(List.of(FetchEventsTool.STREAM_ID_COLUMN, FetchEventsTool.EVENT_ID_COLUMN, column),
                rows, rows.size(), rows.size());
    }

    /// @return The table, with the results added as a column, matched on stream and event ids.
    private Result join(final SessionWorkspace workspace,
                        final WorkspacePath tablePath,
                        final String column,
                        final Map<List<String>, List<String>> results) {
        final Result table = TableEngine.run(workspace.localFile(tablePath), Format.forPath(tablePath),
                new Query(null, null, null, null, false, false, null, null));
        final int streamColumn = indexOf(table.columns(), FetchEventsTool.STREAM_ID_COLUMN);
        final int eventColumn = indexOf(table.columns(), FetchEventsTool.EVENT_ID_COLUMN);
        if (streamColumn < 0 || eventColumn < 0) {
            throw new WorkspaceException("The table to join to has no " + FetchEventsTool.STREAM_ID_COLUMN
                                         + " and " + FetchEventsTool.EVENT_ID_COLUMN + " columns.");
        }
        final Map<List<String>, String> byId = new HashMap<>();
        results.forEach((key, values) -> byId.put(List.of(normalise(key.get(0)), normalise(key.get(1))),
                String.join(VALUE_SEPARATOR, values)));
        final List<String> columns = new ArrayList<>(table.columns());
        columns.add(column);
        final List<List<String>> rows = new ArrayList<>();
        for (final List<String> row : table.rows()) {
            final List<String> joined = new ArrayList<>(row);
            joined.add(byId.getOrDefault(List.of(normalise(row.get(streamColumn)), normalise(row.get(eventColumn))),
                    ""));
            rows.add(joined);
        }
        return new Result(columns, rows, rows.size(), rows.size());
    }

    private static String normalise(final String id) {
        return id.strip().replace(",", "");
    }

    private static int indexOf(final List<String> columns, final String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).strip().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private void writeCsv(final SessionWorkspace workspace, final WorkspacePath out, final Result result,
            final String source) {
        ToolTables.writeCsv(workspace, out, result.columns(), result.rows(), source);
    }

    private static String markdown(final Result result) {
        final StringBuilder sb = new StringBuilder(row(result.columns()));
        sb.append("|").append(" --- |".repeat(result.columns().size())).append('\n');
        result.rows().stream().limit(PREVIEW_ROWS).forEach(row -> sb.append(row(row)));
        if (result.rows().size() > PREVIEW_ROWS) {
            sb.append("\n(").append(result.rows().size() - PREVIEW_ROWS).append(" more rows in the file.)");
        }
        return sb.toString().strip();
    }

    private static String row(final List<String> cells) {
        final StringBuilder sb = new StringBuilder("|");
        for (final String cell : cells) {
            sb.append(' ').append(shorten(cell, 200).replace("|", "\\|")).append(" |");
        }
        return sb.append('\n').toString();
    }

    private static String nonBlankElse(final String value, final String other) {
        return value == null || value.isBlank()
                ? other
                : value.strip();
    }

    /// Counts the XPath values jq couldn't be applied to.
    static final class JqErrors {

        private int notJson;
        private int failed;
        private String firstFailure;

        /// @return A note of the values jq couldn't be applied to, or nothing.
        String summary() {
            final StringBuilder sb = new StringBuilder();
            if (notJson > 0) {
                sb.append(" (").append(notJson).append(" XPath value(s) weren't JSON, so were left out");
            }
            if (failed > 0) {
                sb.append(sb.isEmpty()
                        ? " ("
                        : "; ").append("the jq filter failed on ").append(failed).append(" value(s), e.g. ")
                        .append(firstFailure);
            }
            return sb.isEmpty()
                    ? ""
                    : sb.append(')').toString();
        }
    }
}
