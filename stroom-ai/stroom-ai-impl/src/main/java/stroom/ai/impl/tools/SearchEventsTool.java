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

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Searches each event of a file written by fetch_events (see [EventsFile]): gets values from each by XPath, and/or
/// finds text in each, and writes a table of the results keyed by StreamId and EventId, optionally added as a
/// column to the table the events came from, so they can be analysed with the table tool.
class SearchEventsTool extends AbstractWorkspaceTool {

    static final String NAME = "search_events";
    static final String XPATH_ARG = "xpath";
    static final String NAMESPACES_ARG = "namespaces";
    static final String TEXT_ARG = "text";
    static final String REGEX_ARG = "regex";
    static final String IGNORE_CASE_ARG = "ignoreCase";
    static final String COLUMN_ARG = "column";
    static final String JOIN_ARG = "join";
    static final String DEFAULT_OUT = "/outputs/search_events.csv";
    static final String DEFAULT_PREFIX = "d";
    static final int PREVIEW_ROWS = 10;
    // The most of a value or snippet kept for one event.
    static final int MAX_VALUE_CHARS = 1000;
    static final int SNIPPET_CHARS_BEFORE = 60;
    static final int SNIPPET_CHARS = 200;
    static final String VALUE_SEPARATOR = " | ";

    @Inject
    SearchEventsTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Searches each event in a file from fetch_events, writing a table with one row per "
                             + "event: StreamId, EventId and the result. Give xpath to get a value from each event "
                             + "(evaluated against that event alone, e.g. "
                             + "'//d:EventDetail/d:Authenticate/d:Outcome/d:Reason'; the events' namespace is "
                             + "bound to '" + DEFAULT_PREFIX + "'; several values are joined with ' | '). Give text "
                             + "to keep only the events containing it (in the xpath's value if given, else "
                             + "anywhere in the event), with a snippet around the match. Give join (the table the "
                             + "events came from) to add the result as a column to that table instead. Then use "
                             + "the table tool on the output to count, group or filter.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The events file, e.g. /outputs/events.xml.")
                        .addStringProperty(XPATH_ARG, "An XPath expression to evaluate against each event.")
                        .addProperty(NAMESPACES_ARG, JsonObjectSchema.builder()
                                .description("Namespace prefixes to use, e.g. {\"e\": \"event-logging:3\"}.")
                                .build())
                        .addStringProperty(TEXT_ARG, "Text to find in each event.")
                        .addBooleanProperty(REGEX_ARG, "True if text is a regular expression.")
                        .addBooleanProperty(IGNORE_CASE_ARG, "False to match the case of text (default true).")
                        .addStringProperty(COLUMN_ARG, "A name for the result column (default 'value' with "
                                                       + "xpath, else 'match').")
                        .addStringProperty(JOIN_ARG, "A table with StreamId and EventId columns to add the "
                                                     + "result column to, e.g. /attachments/12.md.")
                        .addStringProperty(OUT_ARG, "A .csv file in /outputs/ to write the table to (default "
                                                    + DEFAULT_OUT + ").")
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
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

        final List<XdmNode> events = events(SafeXPath.parse(workspace.localFile(chatId, path)));
        final XPathExecutable executable = hasXPath
                ? SafeXPath.compile(expression, namespaces(request, events))
                : null;

        // The result for each event that has one, keyed by its stream and event ids.
        final Map<List<String>, String> results = new LinkedHashMap<>();
        for (final XdmNode event : events) {
            ToolDeadline.check();
            final XdmNode content = content(event);
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
            results.put(List.of(event.attribute(EventsFile.STREAM_ID_ATTRIBUTE),
                    event.attribute(EventsFile.EVENT_ID_ATTRIBUTE)), result);
        }

        final String joinArg = rawArg(request, JOIN_ARG);
        final Result table = joinArg != null && !joinArg.isBlank()
                ? join(chatId, WorkspacePath.parse(joinArg), column, results)
                : asTable(column, results);
        writeCsv(chatId, out, table, workspace.sourceDocUuid(chatId, path));
        final String found = pattern != null
                ? results.size() + " of " + events.size() + " event(s) matched"
                : "Searched " + events.size() + " event(s)";
        return found + ". Wrote " + table.rows().size() + " row(s) to " + out + "; use the table tool on it to "
               + "count, group or filter. The first rows:\n\n" + markdown(table);
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
        final List<String> values = new ArrayList<>();
        // A document of its own, so the expression only sees this event.
        SafeXPath.evaluate(executable, SafeXPath.asDocument(content), false, values::add);
        return shorten(String.join(VALUE_SEPARATOR, values).replaceAll("\\s+", " ").strip(), MAX_VALUE_CHARS);
    }

    /// @return The namespaces given, with the events' own namespace bound to [#DEFAULT_PREFIX] unless it is given.
    private static Map<String, String> namespaces(final ToolExecutionRequest request, final List<XdmNode> events) {
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

    private static Result asTable(final String column, final Map<List<String>, String> results) {
        final List<List<String>> rows = new ArrayList<>();
        results.forEach((key, value) -> rows.add(List.of(key.get(0), key.get(1), value)));
        return new Result(List.of(FetchEventsTool.STREAM_ID_COLUMN, FetchEventsTool.EVENT_ID_COLUMN, column),
                rows, rows.size(), rows.size());
    }

    /// @return The table, with the results added as a column, matched on stream and event ids.
    private Result join(final int chatId,
                        final WorkspacePath tablePath,
                        final String column,
                        final Map<List<String>, String> results) {
        final Result table = TableEngine.run(workspace.localFile(chatId, tablePath), Format.forPath(tablePath),
                new Query(null, null, null, null, false, false, null, null));
        final int streamColumn = indexOf(table.columns(), FetchEventsTool.STREAM_ID_COLUMN);
        final int eventColumn = indexOf(table.columns(), FetchEventsTool.EVENT_ID_COLUMN);
        if (streamColumn < 0 || eventColumn < 0) {
            throw new WorkspaceException("The table to join to has no " + FetchEventsTool.STREAM_ID_COLUMN
                                         + " and " + FetchEventsTool.EVENT_ID_COLUMN + " columns.");
        }
        final Map<List<String>, String> byId = new HashMap<>();
        results.forEach((key, value) -> byId.put(List.of(normalise(key.get(0)), normalise(key.get(1))), value));
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

    private void writeCsv(final int chatId, final WorkspacePath out, final Result result, final String source) {
        final Path temp = workspace.tempFile();
        try {
            try (final BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
                    final CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                printer.printRecord(result.columns());
                for (final List<String> row : result.rows()) {
                    printer.printRecord(row);
                }
            }
            workspace.write(chatId, out, temp, source);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            workspace.deleteQuietly(temp);
        }
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
}
