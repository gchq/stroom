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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.query.shared.QueryDoc;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisualisationDoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The components of a dashboard (queries, tables, text and so on), read from the description of it the browser
/// gives the AI chat (each component's heading, e.g. `## Table 'Events' (id table-1)`, and a query's data source),
/// so changes can name them as the user sees them.
public final class DashboardComponents {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DashboardComponents.class);

    /// A query component, as its heading names its type.
    public static final String QUERY = "Query";
    /// A table component.
    public static final String TABLE = "Table";
    /// A text component.
    public static final String TEXT = "Text";
    /// A visualisation (vis) component.
    public static final String VISUALISATION = "Visualisation";
    /// An embedded query component: a StroomQL query, embedded in the dashboard or a Query shown by reference,
    /// with its own results table and chart.
    public static final String EMBEDDED_QUERY = "Embedded Query";
    /// What starts the line of a visualisation component that gives its latest render report. The report is
    /// written by the visualisation's script, so it changes as the visualisation renders: it is left out when
    /// telling whether the dashboard has changed (see [#withoutRenderReports(String)]).
    public static final String RENDER_REPORT = "Render report: ";

    private static final Pattern HEADING = Pattern.compile("^## (.+?) '(.*)' \\(id ([^)]+)\\)$");
    private static final Pattern DATA_SOURCE = Pattern.compile("^Data source: (\\S+) '(.*)' \\(uuid ([^)]+)\\)$");
    private static final Pattern VIS_DOC = Pattern.compile("^Visualisation: '(.*)' \\(uuid ([^)]+)\\)$");
    private static final Pattern LINKED_TABLE = Pattern.compile("^Linked table: '(.*)'$");
    private static final String VIS_SETTINGS = "Vis settings: ";
    private static final Pattern REFERENCED_QUERY = Pattern.compile("^Query: Query '(.*)' \\(uuid ([^)]+)\\) by "
                                                                    + "reference$");
    private static final String STROOM_QL = "StroomQL:";
    // What starts a table's line naming the query that feeds it, e.g. "Query: query-1".
    private static final String TABLE_QUERY = "Query: ";
    // The line above a classic query's expression.
    private static final String EXPRESSION = "Expression:";
    private static final String FENCE = "```";

    private DashboardComponents() {
        // Static methods only.
    }

    /// @param text The dashboard's description, as the browser gives it.
    /// @return Its components, in the order given.
    public static List<Component> parse(final String text) {
        final List<Component> components = new ArrayList<>();
        Component current = null;
        for (final String line : NullSafe.string(text).split("\n")) {
            final Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                if (current != null) {
                    components.add(current);
                }
                current = new Component(heading.group(1), heading.group(2), heading.group(3), null);
                continue;
            }
            if (current == null) {
                continue;
            }
            final Matcher dataSource = DATA_SOURCE.matcher(line);
            final Matcher visDoc = VIS_DOC.matcher(line);
            final Matcher linkedTable = LINKED_TABLE.matcher(line);
            if (dataSource.matches()) {
                current = new Component(current.type(), current.name(), current.id(),
                        new DocRef(dataSource.group(1), dataSource.group(3), dataSource.group(2)),
                        current.visualisation(), current.table(), current.settings());
            } else if (VISUALISATION.equals(current.type()) && visDoc.matches()) {
                current = new Component(current.type(), current.name(), current.id(), current.dataSource(),
                        new DocRef(VisualisationDoc.TYPE, visDoc.group(2), visDoc.group(1)), current.table(),
                        current.settings());
            } else if (VISUALISATION.equals(current.type()) && linkedTable.matches()) {
                current = new Component(current.type(), current.name(), current.id(), current.dataSource(),
                        current.visualisation(), linkedTable.group(1), current.settings());
            } else if (VISUALISATION.equals(current.type()) && line.startsWith(VIS_SETTINGS)) {
                current = new Component(current.type(), current.name(), current.id(), current.dataSource(),
                        current.visualisation(), current.table(),
                        settingsFromJson(line.substring(VIS_SETTINGS.length())));
            }
        }
        if (current != null) {
            components.add(current);
        }
        return List.copyOf(components);
    }

    /// @param text A dashboard's description, as the browser gives it.
    /// @return The description without the lines giving visualisations' render reports, which change as they
    /// render, for telling whether the dashboard itself has changed.
    public static String withoutRenderReports(final String text) {
        if (text == null || !text.contains(RENDER_REPORT)) {
            return text;
        }
        final List<String> kept = new ArrayList<>();
        for (final String line : text.split("\n", -1)) {
            if (!line.startsWith(RENDER_REPORT)) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    // A visualisation's settings, as the description gives them: a JSON object of control ids and values.
    private static Map<String, String> settingsFromJson(final String json) {
        try {
            final Map<?, ?> map = JsonUtil.readValue(json, Map.class);
            if (map == null) {
                return Map.of();
            }
            final Map<String, String> settings = new LinkedHashMap<>();
            map.forEach((key, value) -> settings.put(String.valueOf(key), value == null
                    ? ""
                    : String.valueOf(value)));
            return Collections.unmodifiableMap(settings);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read a visualisation's settings: " + json, e);
            return Map.of();
        }
    }

    /// Reads an embedded query's StroomQL from the dashboard's description.
    ///
    /// @param text        The dashboard's description, as the browser gives it.
    /// @param componentId The embedded query's component id.
    /// @return Its StroomQL and the Query it shows by reference, if it does; empty if there is no such embedded
    /// query, or its StroomQL isn't given in full.
    public static Optional<EmbeddedQuery> embeddedQuery(final String text, final String componentId) {
        boolean in = false;
        boolean opened = false;
        DocRef referenced = null;
        StringBuilder query = null;
        for (final String line : NullSafe.string(text).split("\n", -1)) {
            if (query != null) {
                // Within the StroomQL: up to the closing fence.
                if (FENCE.equals(line)) {
                    return Optional.of(new EmbeddedQuery(query.toString().strip(), referenced));
                }
                query.append(line).append('\n');
                continue;
            }
            final Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                if (in) {
                    break;
                }
                in = EMBEDDED_QUERY.equals(heading.group(1)) && heading.group(3).equals(componentId);
                continue;
            }
            if (!in) {
                continue;
            }
            final Matcher reference = REFERENCED_QUERY.matcher(line);
            if (reference.matches()) {
                referenced = new DocRef(QueryDoc.TYPE, reference.group(2), reference.group(1));
            } else if (STROOM_QL.equals(line)) {
                opened = true;
            } else if (opened && FENCE.equals(line)) {
                query = new StringBuilder();
            }
        }
        return Optional.empty();
    }

    /// Reads the expression of the classic query that feeds a table from the dashboard's description: the line
    /// after the query's `Expression:` line, and the indented lines below it (one condition a line).
    ///
    /// @param text    The dashboard's description, as the browser gives it.
    /// @param tableId The table's component id.
    /// @return The expression's lines, if the table is fed by a classic query on the dashboard.
    public static Optional<String> queryExpression(final String text, final String tableId) {
        final String[] lines = NullSafe.string(text).split("\n", -1);
        String queryId = null;
        String section = null;
        for (final String line : lines) {
            final Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                section = TABLE.equals(heading.group(1))
                        ? heading.group(3)
                        : null;
            } else if (tableId.equals(section) && line.startsWith(TABLE_QUERY)) {
                queryId = line.substring(TABLE_QUERY.length()).strip();
            }
        }
        if (queryId == null) {
            return Optional.empty();
        }
        boolean inQuery = false;
        StringBuilder expression = null;
        for (final String line : lines) {
            final Matcher heading = HEADING.matcher(line);
            if (expression != null) {
                if (heading.matches() || (!expression.isEmpty() && !line.startsWith(" "))) {
                    break;
                }
                expression.append(line).append('\n');
            } else if (heading.matches()) {
                inQuery = QUERY.equals(heading.group(1)) && heading.group(3).equals(queryId);
            } else if (inQuery && EXPRESSION.equals(line)) {
                expression = new StringBuilder();
            }
        }
        return Optional.ofNullable(expression).map(StringBuilder::toString);
    }

    /// @param components A dashboard's components.
    /// @param name       A component's name, as the user sees it.
    /// @return The component with the name: exactly, else ignoring case if only one matches.
    public static Optional<Component> find(final List<Component> components, final String name) {
        if (name == null) {
            return Optional.empty();
        }
        final Optional<Component> exact = components.stream().filter(c -> name.equals(c.name())).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        final List<Component> matches = components.stream().filter(c -> name.equalsIgnoreCase(c.name())).toList();
        return matches.size() == 1
                ? Optional.of(matches.getFirst())
                : Optional.empty();
    }

    /// @param components A dashboard's components.
    /// @return Each named with its type, e.g. "Table 'Events'", for telling the model what there is.
    public static String describe(final List<Component> components) {
        return String.join(", ", components.stream().map(c -> c.type() + " '" + c.name() + "'").toList());
    }


    // --------------------------------------------------------------------------------


    /// A dashboard component.
    ///
    /// @param type       Its type, as its heading names it, e.g. [#TABLE].
    /// @param name       Its name, as the user sees it.
    /// @param id         Its component id.
    /// @param dataSource    For a query, its data source, or null.
    /// @param visualisation For a visualisation, the Visualisation document it shows, or null.
    /// @param table         For a visualisation, the name of the table it is linked to, or null.
    /// @param settings      For a visualisation, its settings: each control's id and value. Empty for others.
    public record Component(String type,
                            String name,
                            String id,
                            DocRef dataSource,
                            DocRef visualisation,
                            String table,
                            Map<String, String> settings) {

        /// @param type       Its type, as its heading names it, e.g. [#TABLE].
        /// @param name       Its name, as the user sees it.
        /// @param id         Its component id.
        /// @param dataSource For a query, its data source, or null.
        public Component(final String type, final String name, final String id, final DocRef dataSource) {
            this(type, name, id, dataSource, null, null, Map.of());
        }
    }

    /// An embedded query's StroomQL.
    ///
    /// @param query      Its StroomQL, as the dashboard has it.
    /// @param referenced The Query it shows by reference, whose StroomQL it is, or null if it is embedded in the
    ///                   dashboard.
    public record EmbeddedQuery(String query, DocRef referenced) {

        /// @return True if its StroomQL is a Query's, shown by reference, so changed in that Query.
        public boolean isReference() {
            return referenced != null;
        }
    }
}
