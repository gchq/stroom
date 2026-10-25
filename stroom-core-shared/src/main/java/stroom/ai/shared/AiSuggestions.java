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

package stroom.ai.shared;

import stroom.dashboard.shared.DashboardDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.shared.QueryDoc;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What the user might ask about what they attach to a chat (a tab, a document from the Explorer, or a table of
/// results), and what the agent can do with it, so suggestions guide the user towards what is possible for that
/// kind of thing. Used by the chat when something is attached (to offer questions straight away) and by the
/// server's suggestions (to bias them towards what is attached, given the conversation so far).
public final class AiSuggestions {

    /// The most suggestions offered.
    public static final int MAX_SUGGESTIONS = 3;
    /// The most characters of a name (a document's, a table's or an element's) put in a suggestion.
    static final int MAX_NAME_CHARS = 40;
    /// The most tables named in the hint for the follow-up suggestions.
    static final int MAX_HINT_TABLES = 3;
    /// The types of the data sources the agent can describe and query with StroomQL.
    static final List<String> DATA_SOURCE_TYPES = List.of("Index", "ElasticIndex", "SolrIndex", "StatisticStore",
            "StroomStatsStore", "View", "ScyllaDB");
    static final String FEED_TYPE = "Feed";
    static final String DOCUMENTATION_TYPE = "Documentation";
    static final String ONBOARD_THIS_FEED = "Onboard this feed (translate and index its data)";
    static final String ONBOARD_INTO_FOLDER = "Onboard a new data source into this folder";
    static final String SUGGEST_A_CHART = "Suggest a chart for this dashboard";

    private AiSuggestions() {
        // Static methods only.
    }

    /// @param snapshot The attached tab, as the user sees it, or null.
    /// @return Questions the user might ask about it, most likely first, at most [#MAX_SUGGESTIONS]; empty if
    /// there is no tab.
    public static List<String> forTab(final AiTabSnapshot snapshot) {
        if (snapshot == null || snapshot.getDocRef() == null) {
            return new ArrayList<>();
        }
        return forTab(snapshot.getDocRef(), snapshot.isDirty(), snapshot.getSearches(), snapshot.getStepping(),
                dashboardDescription(snapshot));
    }

    /// @param docRef   The tab's document.
    /// @param dirty    True if it has unsaved changes.
    /// @param searches The tables of results it shows, if any.
    /// @param stepping Its stepping screen, if it is stepping a pipeline.
    /// @return Questions the user might ask about it, most likely first, at most [#MAX_SUGGESTIONS].
    public static List<String> forTab(final DocRef docRef,
                                      final boolean dirty,
                                      final List<AiTabSearch> searches,
                                      final AiSteppingContext stepping) {
        return forTab(docRef, dirty, searches, stepping, null);
    }

    /// @param docRef      The tab's document.
    /// @param dirty       True if it has unsaved changes.
    /// @param searches    The tables of results it shows, if any.
    /// @param stepping    Its stepping screen, if it is stepping a pipeline.
    /// @param description For a dashboard, its description (its [AiTabSnapshotPart#DASHBOARD] part, or the outline
    ///                    of it), or null if not known.
    /// @return Questions the user might ask about it, most likely first, at most [#MAX_SUGGESTIONS].
    public static List<String> forTab(final DocRef docRef,
                                      final boolean dirty,
                                      final List<AiTabSearch> searches,
                                      final AiSteppingContext stepping,
                                      final String description) {
        final List<String> suggestions = new ArrayList<>();
        final String type = NullSafe.string(NullSafe.get(docRef, DocRef::getType));
        if (stepping != null) {
            addStepping(suggestions, stepping);
        } else if (QueryDoc.TYPE.equals(type)) {
            addQuery(suggestions, dirty, firstTable(searches));
        } else if (DashboardDoc.TYPE.equals(type)) {
            addDashboard(suggestions, NullSafe.list(searches), DashboardOutline.read(description));
        } else if (PipelineDoc.TYPE.equals(type)) {
            suggestions.add("Explain how this pipeline processes its data");
            suggestions.add("Which feeds does this pipeline process?");
            suggestions.add("Where does this pipeline's output go?");
        } else if (XsltDoc.TYPE.equals(type) || TextConverterDoc.TYPE.equals(type)) {
            final String what = XsltDoc.TYPE.equals(type)
                    ? "XSLT"
                    : "text converter";
            if (dirty) {
                suggestions.add("Review my unsaved changes to this " + what);
            }
            suggestions.add("Explain what this " + what + " does");
            suggestions.add("Check this " + what + " for mistakes");
            suggestions.add("Which pipelines use this " + what + "?");
        } else if (DictionaryDoc.TYPE.equals(type)) {
            suggestions.add("Explain what this dictionary is for");
            suggestions.add("What uses this dictionary?");
        } else if (FEED_TYPE.equals(type)) {
            // Onboarding first: a feed is what the agent's onboarding works from (asked for by the user).
            suggestions.add(ONBOARD_THIS_FEED);
            suggestions.add("What data has this feed received recently?");
            suggestions.add("Are there processing errors for this feed?");
        } else if (ExplorerConstants.FOLDER_TYPE.equals(type)) {
            suggestions.add("What's in this folder?");
            suggestions.add("Summarise what this folder is for");
            suggestions.add(ONBOARD_INTO_FOLDER);
        } else {
            final String what = NullSafe.nonBlankStringElse(type, "document");
            suggestions.add("Explain this " + what);
            suggestions.add("What uses this " + what + "?");
        }
        return suggestions.size() > MAX_SUGGESTIONS
                ? new ArrayList<>(suggestions.subList(0, MAX_SUGGESTIONS))
                : suggestions;
    }

    /// @param snapshot The attached tab, as the user sees it, or null.
    /// @return The tab with only what names it and what suggestions are made from (its document, the tab shown,
    /// whether it is unsaved, its tables' names and row counts, and the selected stepping element and its error
    /// counts), without its content, to send when asking for suggestions; or null if there is no tab.
    public static AiTabSnapshot outline(final AiTabSnapshot snapshot) {
        if (snapshot == null || snapshot.getDocRef() == null) {
            return null;
        }
        final List<AiTabSearch> tables = new ArrayList<>();
        for (final AiTabSearch search : NullSafe.list(snapshot.getSearches())) {
            if (search != null && tables.size() < MAX_HINT_TABLES) {
                tables.add(new AiTabSearch(search.getName(), null, null, null, null, search.getVisibleRows(),
                        search.isSearching()));
            }
        }
        final AiSteppingContext stepping = snapshot.getStepping();
        AiSteppingContext steppingOutline = null;
        if (stepping != null) {
            final AiSteppingElement element = stepping.getSelectedElement();
            final List<AiSteppingElement> elements = new ArrayList<>();
            if (element != null) {
                elements.add(new AiSteppingElement(element.getId(), element.getType(), element.getDocRef(),
                        element.isHasCode(), element.isDirty(), element.getErrorCount(), element.getWarningCount(),
                        null));
            }
            steppingOutline = new AiSteppingContext(null, null, null, null, null, null,
                    stepping.getSelectedElementId(), elements,
                    NullSafe.isEmptyCollection(stepping.getGeneralErrors())
                            ? null
                            : new ArrayList<>(stepping.getGeneralErrors().subList(0, 1)));
        }
        // A dashboard's structure, which its suggestions are made from, without its content.
        final String structure = DashboardOutline.read(dashboardDescription(snapshot)).write();
        final List<AiTabSnapshotPart> parts = structure.isEmpty()
                ? null
                : List.of(new AiTabSnapshotPart(AiTabSnapshotPart.DASHBOARD, null, structure));
        return new AiTabSnapshot(snapshot.getDocRef(), snapshot.getTabLabel(), snapshot.getInnerTab(),
                snapshot.isDirty(), parts, tables, null, steppingOutline, null);
    }

    /// @param docRef A document the user attached to the chat from the Explorer.
    /// @return Questions the user might ask about it, most likely first, at most [#MAX_SUGGESTIONS].
    public static List<String> forDocument(final DocRef docRef) {
        final List<String> suggestions = new ArrayList<>();
        if (docRef == null) {
            return suggestions;
        }
        final String type = NullSafe.string(docRef.getType());
        final String name = "'" + shorten(NullSafe.nonBlankStringElse(docRef.getName(), docRef.getUuid())) + "'";
        if (FEED_TYPE.equals(type)) {
            // Onboarding first: a feed is what the agent's onboarding works from (asked for by the user).
            suggestions.add("Onboard " + name + " (translate and index its data)");
            suggestions.add("What data has " + name + " received recently?");
            suggestions.add("Are there processing errors for " + name + "?");
        } else if (DATA_SOURCE_TYPES.contains(type)) {
            suggestions.add("What fields does " + name + " have?");
            suggestions.add("Query " + name + " for its latest events");
            suggestions.add("Which pipelines write to " + name + "?");
        } else if (ExplorerConstants.FOLDER_TYPE.equals(type)) {
            suggestions.add("What's in the " + name + " folder?");
            suggestions.add("Summarise what the " + name + " folder is for");
            suggestions.add("Onboard a new data source into the " + name + " folder");
        } else if (PipelineDoc.TYPE.equals(type)) {
            suggestions.add("Explain how " + name + " processes its data");
            suggestions.add("Which feeds does " + name + " process?");
            suggestions.add("Where does the output of " + name + " go?");
        } else if (XsltDoc.TYPE.equals(type) || TextConverterDoc.TYPE.equals(type)) {
            suggestions.add("Explain what " + name + " does");
            suggestions.add("Check " + name + " for mistakes");
            suggestions.add("Which pipelines use " + name + "?");
        } else if (DashboardDoc.TYPE.equals(type)) {
            suggestions.add("Explain what " + name + " shows and where its data comes from");
            suggestions.add("Which data sources does " + name + " use?");
        } else if (QueryDoc.TYPE.equals(type)) {
            suggestions.add("Explain what " + name + " does");
            suggestions.add("Run " + name + " and summarise the results");
        } else if (DOCUMENTATION_TYPE.equals(type)) {
            suggestions.add("Summarise " + name);
            suggestions.add("What does " + name + " apply to?");
        } else {
            suggestions.add("Explain " + name);
            suggestions.add("What uses " + name + "?");
        }
        return suggestions;
    }

    /// @return Questions the user might ask about a table of results they attached.
    public static List<String> forTable() {
        final List<String> suggestions = new ArrayList<>();
        suggestions.add("What stands out in the attached table?");
        suggestions.add("Summarise the attached table");
        suggestions.add("Count the attached table's rows by their most useful column");
        return suggestions;
    }

    /// Takes suggestions from each group in turn (the first of each, then the second, and so on), so each thing
    /// attached gets its most likely question, the most recently attached first.
    ///
    /// @param groups Suggestions for each thing attached, most recently attached first.
    /// @return At most [#MAX_SUGGESTIONS] suggestions, without repeats.
    public static List<String> combine(final List<List<String>> groups) {
        final List<String> combined = new ArrayList<>();
        if (groups == null) {
            return combined;
        }
        for (int i = 0; combined.size() < MAX_SUGGESTIONS; i++) {
            boolean any = false;
            for (final List<String> group : groups) {
                if (group != null && i < group.size()) {
                    any = true;
                    if (!combined.contains(group.get(i)) && combined.size() < MAX_SUGGESTIONS) {
                        combined.add(group.get(i));
                    }
                }
            }
            if (!any) {
                break;
            }
        }
        return combined;
    }

    /// A short hint, for the model suggesting what to ask, of a document the user attached from the Explorer and
    /// what the agent can do with it: two lines.
    ///
    /// @param docRef The document.
    /// @return The hint, or an empty string if there is no document.
    public static String documentHint(final DocRef docRef) {
        if (docRef == null) {
            return "";
        }
        final String type = NullSafe.nonBlankStringElse(docRef.getType(), "document");
        return "The user has attached " + type + " '" + shorten(docRef.getName()) + "' from the Explorer.\n"
               + "With it, the assistant can " + capabilities(type, false) + ".";
    }

    /// A short hint, for the model suggesting what to ask, of a table of results the user attached: two lines.
    ///
    /// @param description The table, as described, e.g. "Dashboard 'Logons' -> Table 'By user'".
    /// @return The hint.
    public static String tableHint(final String description) {
        return "The user has attached a table of results: " + shorten(NullSafe.nonBlankStringElse(description,
                "a table")) + ".\nWith it, the assistant can profile and analyse its rows, count and group them, "
               + "and look into the events behind them.";
    }

    /// A short hint, for the model suggesting what to ask, of the tab attached to the chat and what the agent can
    /// do with it: one line naming it (and at most [#MAX_HINT_TABLES] of its tables), and one sentence of what can
    /// be done. Kept short, as it is sent with every request for suggestions.
    ///
    /// @param docRef   The tab's document.
    /// @param innerTab The tab within it that is shown (e.g. "Stepping"), or null.
    /// @param dirty    True if it has unsaved changes.
    /// @param searches The tables of results it shows, if any.
    /// @param stepping Its stepping screen, if it is stepping a pipeline.
    /// @return The hint, or an empty string if there is no tab.
    public static String tabHint(final DocRef docRef,
                              final String innerTab,
                              final boolean dirty,
                              final List<AiTabSearch> searches,
                              final AiSteppingContext stepping) {
        if (docRef == null) {
            return "";
        }
        final String type = NullSafe.nonBlankStringElse(docRef.getType(), "document");
        final StringBuilder sb = new StringBuilder("The user has ")
                .append(type).append(" '").append(shorten(docRef.getName())).append("' attached to the chat");
        if (stepping != null) {
            final AiSteppingElement element = stepping.getSelectedElement();
            sb.append(", stepping a record");
            if (element != null) {
                sb.append(" with element '").append(shorten(element.getId())).append("' selected");
            }
        } else if (NullSafe.isNonBlankString(innerTab)) {
            sb.append(", showing its ").append(innerTab).append(" tab");
        }
        if (dirty) {
            sb.append(" (unsaved changes)");
        }
        final List<String> tables = tableNames(searches);
        if (!tables.isEmpty()) {
            sb.append("; tables: ").append(String.join(", ", tables));
        }
        return sb.append(".\nWith it, the assistant can ").append(capabilities(type, stepping != null)).append(".")
                .toString();
    }

    /// @return What the agent can do with a tab of the given type, as part of a sentence.
    static String capabilities(final String type, final boolean stepping) {
        if (stepping) {
            return "explain what the selected element does with the record and why its output is as it is, and "
                   + "propose a change to it (tried on the record) for the user to review and apply";
        } else if (QueryDoc.TYPE.equals(type)) {
            return "explain or refine the query and offer it with an 'Apply to query' button, analyse the results "
                   + "shown, and look into the events behind them";
        } else if (DashboardDoc.TYPE.equals(type)) {
            return "analyse a table's rows, propose changes to its tables (group, columns, sort, filter, "
                   + "formatting) with an 'Apply to dashboard' button, suggest and create a chart that suits a "
                   + "table's data, make a chart filter the dashboard by what is clicked on it, and look into the "
                   + "events behind the rows";
        } else if (PipelineDoc.TYPE.equals(type)) {
            return "explain its structure, find the feeds it processes and where its output goes, and help step a "
                   + "record through it";
        } else if (XsltDoc.TYPE.equals(type) || TextConverterDoc.TYPE.equals(type)) {
            return "explain and review it (unsaved changes included), and find its lookups and the pipelines that "
                   + "use it";
        } else if (DictionaryDoc.TYPE.equals(type)) {
            return "explain it, with its imports, and find what uses it";
        } else if (FEED_TYPE.equals(type)) {
            return "onboard it (build and test a translation of its data to events, document it, and index the "
                   + "events), find the streams it has received and their status, any processing errors, and the "
                   + "pipelines that process it";
        } else if (DATA_SOURCE_TYPES.contains(type)) {
            return "describe its fields and documentation, query it with StroomQL, and find the pipelines that "
                   + "write to it";
        } else if (DOCUMENTATION_TYPE.equals(type)) {
            return "read it and answer from it";
        } else if (ExplorerConstants.FOLDER_TYPE.equals(type)) {
            return "list and explain what it contains, and onboard a new data source into it from sample data";
        }
        return "read and explain it, and find what uses it";
    }

    private static void addStepping(final List<String> suggestions, final AiSteppingContext stepping) {
        final AiSteppingElement element = stepping.getSelectedElement();
        final boolean errors = !NullSafe.isEmptyCollection(stepping.getGeneralErrors())
                               || (element != null && element.getErrorCount() > 0);
        if (element == null) {
            if (errors) {
                suggestions.add("Why is this record giving errors?");
            }
            suggestions.add("Explain how this record is processed");
            return;
        }
        final String name = "'" + shorten(element.getId()) + "'";
        if (errors) {
            suggestions.add("Why is " + name + " giving errors for this record, and how do I fix it?");
        }
        suggestions.add("Explain what " + name + " does with this record");
        final String codeType = NullSafe.get(element.getDocRef(), DocRef::getType);
        if (XsltDoc.TYPE.equals(codeType) || TextConverterDoc.TYPE.equals(codeType)) {
            suggestions.add("Which input fields does " + name + " leave out of its output?");
        }
    }

    private static void addQuery(final List<String> suggestions, final boolean dirty, final AiTabSearch table) {
        if (table != null && !table.isSearching() && Long.valueOf(0).equals(table.getVisibleRows())) {
            suggestions.add("Why does this query return no results?");
        } else if (table != null) {
            suggestions.add("What stands out in these results?");
        }
        suggestions.add(dirty
                ? "Explain what my changes to this query do"
                : "Explain what this query does");
        suggestions.add("Count these results by their most useful field");
    }

    // A chart first, when a table that has rows has none yet: the agent picks one from the data, the table's settings
    // and the charts already there. Then why a table is empty, filtering by a chart Stroom AI drew (whose clicks
    // select what is clicked) if nothing filters by one yet, and the first table's rows.
    private static void addDashboard(final List<String> suggestions,
                                     final List<AiTabSearch> searches,
                                     final DashboardOutline outline) {
        // A chart that draws nothing is the most pressing: the agent finds out why (e.g. a selection filter).
        if (!outline.emptyCharts.isEmpty()) {
            suggestions.add("Why does the '" + shorten(outline.emptyCharts.get(0)) + "' chart show nothing?");
        }
        AiTabSearch empty = null;
        boolean unchartedRows = false;
        for (final AiTabSearch search : searches) {
            if (search == null) {
                continue;
            }
            final boolean noRows = !search.isSearching() && Long.valueOf(0).equals(search.getVisibleRows());
            if (noRows && empty == null) {
                empty = search;
            } else if (!noRows && !outline.isCharted(search.getName())) {
                unchartedRows = true;
            }
        }
        if (unchartedRows) {
            suggestions.add(SUGGEST_A_CHART);
        }
        if (empty != null) {
            suggestions.add("Why does the '" + shorten(empty.getName()) + "' table show no results?");
        }
        if (!outline.clickableCharts.isEmpty() && !outline.selectionFilters) {
            suggestions.add("Filter the dashboard by what I click on the '" + shorten(outline.clickableCharts.get(0))
                            + "' chart");
        }
        final AiTabSearch table = firstTable(searches);
        if (table != null) {
            final String name = "'" + shorten(table.getName()) + "'";
            suggestions.add("What stands out in the " + name + " table?");
            if (!outline.groupedTables.contains(table.getName())) {
                suggestions.add("Group the " + name + " table by its most useful column");
            }
        }
        suggestions.add("Explain what this dashboard shows and where its data comes from");
    }

    // The dashboard's description in a tab, or null if it isn't one (or isn't known).
    private static String dashboardDescription(final AiTabSnapshot snapshot) {
        if (snapshot == null || !DashboardDoc.TYPE.equals(NullSafe.get(snapshot.getDocRef(), DocRef::getType))) {
            return null;
        }
        for (final AiTabSnapshotPart part : NullSafe.list(snapshot.getParts())) {
            if (part != null && AiTabSnapshotPart.DASHBOARD.equals(part.getName())) {
                return part.getText();
            }
        }
        return null;
    }

    private static AiTabSearch firstTable(final List<AiTabSearch> searches) {
        return NullSafe.isEmptyCollection(searches)
                ? null
                : searches.get(0);
    }

    private static List<String> tableNames(final List<AiTabSearch> searches) {
        final List<String> names = new ArrayList<>();
        if (searches != null) {
            for (final AiTabSearch search : searches) {
                if (search != null && NullSafe.isNonBlankString(search.getName())
                    && names.size() < MAX_HINT_TABLES) {
                    names.add("'" + shorten(search.getName()) + "'");
                }
            }
        }
        return names;
    }

    private static String shorten(final String name) {
        final String value = NullSafe.string(name).trim();
        return value.length() > MAX_NAME_CHARS
                ? value.substring(0, MAX_NAME_CHARS - 1) + "…"
                : value;
    }


    // --------------------------------------------------------------------------------


    /// What a dashboard has, read from its description (see the client's `DashboardPresenter#describe`): its tables
    /// (and embedded queries), which of them are grouped, its charts and the tables they are linked to, which charts
    /// Stroom AI drew (by their render reports), and whether anything filters by a chart's selection.
    static final class DashboardOutline {

        private static final String HEADING = "## ";
        private static final String ID = "' (id ";
        private static final String GROUPED = "(grouped";
        private static final String LINKED_TABLE = "Linked table: ";
        private static final String QUERY_SELECTION_FILTERS = "Query selection filters: ";
        private static final String TABLE_SELECTION_FILTERS = "Table selection filters: ";
        private static final String RENDER_REPORT = "Render report: ";
        private static final String GENERATED_ADAPTER = "\"adapter\":\"ECharts Generic\"";
        private static final List<String> NOTHING_DRAWN = List.of("empty", "nothing-drawn", "error");
        private static final String TABLE = "Table";
        private static final String EMBEDDED_QUERY = "Embedded Query";
        private static final String VISUALISATION = "Visualisation";

        private boolean known;
        private final List<String> tables = new ArrayList<>();
        private final List<String> groupedTables = new ArrayList<>();
        // Each chart, and the table it is linked to (null if none).
        private final Map<String, String> charts = new LinkedHashMap<>();
        private final List<String> clickableCharts = new ArrayList<>();
        // The charts whose render reports say they drew nothing: empty, nothing drawn, or an error.
        private final List<String> emptyCharts = new ArrayList<>();
        private boolean selectionFilters;

        static DashboardOutline read(final String description) {
            final DashboardOutline outline = new DashboardOutline();
            if (description == null) {
                return outline;
            }
            outline.known = true;
            String type = null;
            String name = null;
            for (final String line : description.split("\n")) {
                if (line.startsWith(HEADING)) {
                    final int quote = line.indexOf('\'');
                    final int end = line.lastIndexOf(ID);
                    type = quote > HEADING.length()
                            ? line.substring(HEADING.length(), quote).trim()
                            : null;
                    name = quote >= 0 && end > quote
                            ? line.substring(quote + 1, end)
                            : null;
                    if (name != null && (TABLE.equals(type) || EMBEDDED_QUERY.equals(type))) {
                        outline.tables.add(name);
                    } else if (name != null && VISUALISATION.equals(type)) {
                        outline.charts.put(name, null);
                    }
                } else if (name == null) {
                    continue;
                } else if (line.startsWith(QUERY_SELECTION_FILTERS) || line.startsWith(TABLE_SELECTION_FILTERS)) {
                    outline.selectionFilters = true;
                } else if (TABLE.equals(type) && line.startsWith("- ") && line.contains(GROUPED)) {
                    if (!outline.groupedTables.contains(name)) {
                        outline.groupedTables.add(name);
                    }
                } else if (VISUALISATION.equals(type) && line.startsWith(LINKED_TABLE)) {
                    final String table = line.substring(LINKED_TABLE.length()).trim();
                    if (table.length() > 1 && table.startsWith("'") && table.endsWith("'")) {
                        outline.charts.put(name, table.substring(1, table.length() - 1));
                    }
                } else if (VISUALISATION.equals(type) && line.startsWith(RENDER_REPORT)) {
                    final String report = line.replace(" ", "");
                    if (report.contains(GENERATED_ADAPTER.replace(" ", ""))) {
                        outline.clickableCharts.add(name);
                    }
                    for (final String status : NOTHING_DRAWN) {
                        if (report.contains("\"status\":\"" + status + "\"") && !outline.emptyCharts.contains(name)) {
                            outline.emptyCharts.add(name);
                        }
                    }
                }
            }
            return outline;
        }

        // True if a chart is linked to the table. False if the dashboard isn't known.
        boolean isCharted(final String table) {
            return known && charts.containsValue(table);
        }

        // The outline as a description with only what it is read from, which reads back the same.
        String write() {
            final StringBuilder sb = new StringBuilder();
            for (final String table : tables) {
                sb.append(HEADING).append(TABLE).append(" '").append(table).append(ID).append("-)\n");
                if (groupedTables.contains(table)) {
                    sb.append("- ").append(GROUPED).append(")\n");
                }
            }
            for (final Map.Entry<String, String> chart : charts.entrySet()) {
                sb.append(HEADING).append(VISUALISATION).append(" '").append(chart.getKey()).append(ID)
                        .append("-)\n");
                if (chart.getValue() != null) {
                    sb.append(LINKED_TABLE).append('\'').append(chart.getValue()).append("'\n");
                }
                final List<String> report = new ArrayList<>();
                if (emptyCharts.contains(chart.getKey())) {
                    report.add("\"status\":\"empty\"");
                }
                if (clickableCharts.contains(chart.getKey())) {
                    report.add(GENERATED_ADAPTER);
                }
                if (!report.isEmpty()) {
                    sb.append(RENDER_REPORT).append('{').append(String.join(",", report)).append("}\n");
                }
            }
            // Under whichever component was written last: only whether there are any is kept.
            if (selectionFilters && (!tables.isEmpty() || !charts.isEmpty())) {
                sb.append(TABLE_SELECTION_FILTERS).append("...\n");
            }
            return sb.toString();
        }
    }
}
