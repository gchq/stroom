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


package stroom.gwt.workbench.client.app.dashboard;

/// The dashboards of React's `App/Editors/DashboardEditor` stories as Stroom's `DashboardDoc` JSON,
/// with React's ids, names and settings.
///
/// Differs from React: React's documents are the port's (e.g. `TabLayoutConfig` for Stroom's
/// `tabLayout`); these are Stroom's shapes. Every dashboard but the legacy one has the model version
/// `7.2.0` that Stroom saves: Stroom treats a dashboard without one as legacy and adds a 'Params'
/// input to it (`DashboardPresenter.onRead`), which React does only for the legacy story's.
public final class DashboardDocs {

    // Differs from React: every layout has a preferred size, as Stroom saves them; React's leave some
    // out, which GWT's FlexLayout can't lay out (a NullPointerException in recalculateDimension, or
    // in moveTabOutside when docking)
    private static final String DEFAULT_SIZE = ", \"preferredSize\": {\"width\": 200, \"height\": 100}";

    /// An index data source, as React's `{type: 'Index', uuid: 'idx', name: 'idx'}`.
    public static final String INDEX = "{\"type\": \"Index\", \"uuid\": \"idx\", \"name\": \"idx\"}";

    /// The column `Name` (`f-name`) of React's tables.
    public static final String NAME_FIELD = field("f-name", "Name");

    /// A Query component `q1`, 'The Query', searching [#INDEX].
    public static final String THE_QUERY = query("q1", "The Query", "");

    /// A Table component `t1`, 'The Table', showing the results of [#THE_QUERY].
    public static final String THE_TABLE = table("t1", "The Table", "q1", NAME_FIELD, "");

    /// React's `DASHBOARD_DOC`: one Embedded Query panel ('My Panel', copy mode) in a tab layout.
    public static final String DASHBOARD_DOC = doc("dash-1", "My Dashboard", "\"description\": \"# Dashboard docs\"",
            "\"designMode\": false, \"timeRange\": {\"name\": \"All time\"}",
            tabs(0, "eq1"),
            component("embedded-query", "eq1", "My Panel", "\"reference\": false, \"showTable\": true, "
                    + "\"embeddedQueryDoc\": {\"uuid\": \"eq-inline\", \"type\": \"Query\", \"name\": \"inline\", "
                    + "\"query\": \"from index\\nselect name\\n\"}"));

    /// React's `SPLIT_DASHBOARD`: a reference-mode panel (the query `q-ref`) beside a copy-mode one.
    public static final String SPLIT_DASHBOARD = doc("dash-2", "Split Dashboard", null, "\"designMode\": false",
            split(0, sized(tabs(0, "left"), 300, 100), sized(tabs(0, "right"), 300, 100)),
            component("embedded-query", "left", "Left Panel", "\"reference\": true, "
                    + "\"queryRef\": {\"type\": \"Query\", \"uuid\": \"q-ref\", \"name\": \"Referenced Query\"}"),
            component("embedded-query", "right", "Right Panel", "\"reference\": false, "
                    + "\"embeddedQueryDoc\": {\"query\": \"from index\\nselect count\\n\"}"));

    /// React's `QUERY_TABLE_DASHBOARD`: [#THE_QUERY] and [#THE_TABLE] side by side.
    public static final String QUERY_TABLE_DASHBOARD = doc("dash-3", "Query + Table", null, "\"designMode\": false",
            split(0, sized(tabs(0, "q1"), 300, 100), sized(tabs(0, "t1"), 400, 100)),
            THE_QUERY,
            THE_TABLE);

    /// React's `QUERY_TABLE_VIS_DASHBOARD`: a Query, a Table and a Visualisation of the table, in one
    /// tab layout with the visualisation selected.
    public static final String QUERY_TABLE_VIS_DASHBOARD = doc("dash-4", "Query + Table + Vis", null,
            "\"designMode\": false",
            tabs(2, "q1", "t1", "v1"),
            query("q1", "Q", ""),
            table("t1", "T", "q1", NAME_FIELD, ""),
            vis("v1", "Chart"));

    /// React's `QUERY_TABLE_TEXT_DASHBOARD`: a Query, a Table with stream and event ids, and a Text
    /// component showing the source of the table's selected row.
    public static final String QUERY_TABLE_TEXT_DASHBOARD = queryTableText("dash-5", "");

    /// React's `QUERY_TABLE_HTML_DASHBOARD`: as [#QUERY_TABLE_TEXT_DASHBOARD], with the Text
    /// component showing its source as HTML through a pipeline.
    public static final String QUERY_TABLE_HTML_DASHBOARD = queryTableText("dash-html",
            ", \"showAsHtml\": true, \"pipeline\": {\"type\": \"Pipeline\", \"uuid\": \"p1\", \"name\": \"HTML\"}");

    /// React's `QUERY_TABLE_TEXT_STEP_DASHBOARD`: as [#QUERY_TABLE_TEXT_DASHBOARD], with the Text
    /// component's Step button.
    public static final String QUERY_TABLE_TEXT_STEP_DASHBOARD = queryTableText("dash-step",
            ", \"showStepping\": true");

    /// React's `SELECTION_DRIVEN_DASHBOARD`: a master Query and Table, and a detail Query whose
    /// selection handler queries the master table's selected `Name`, with its Table.
    public static final String SELECTION_DRIVEN_DASHBOARD = doc("dash-6", "Selection-driven", null,
            "\"designMode\": false",
            split(0, sized(tabs(0, "q1"), 250, 100), sized(tabs(0, "t1"), 250, 100),
                    sized(tabs(0, "q2"), 250, 100), sized(tabs(0, "t2"), 250, 100)),
            query("q1", "Master Query", ""),
            table("t1", "Master Table", "q1", NAME_FIELD, ""),
            query("q2", "Detail Query", ", \"selectionHandlers\": [{\"id\": \"h1\", \"enabled\": true, "
                    + "\"expression\": " + operator(term("Name", "EQUALS", "${component.t1.selection.Name}"))
                    + "}]"),
            table("t2", "Detail Table", "q2", field("f-detail", "Detail"), ""));

    /// React's `INPUT_DRIVEN_DASHBOARD`: a Text Input (key `name`, value `alpha`), a Query and a Table.
    public static final String INPUT_DRIVEN_DASHBOARD = doc("dash-7", "Input-driven", null, "\"designMode\": false",
            split(0, sized(tabs(0, "in1"), 250, 100), sized(tabs(0, "q1"), 250, 100),
                    sized(tabs(0, "t1"), 300, 100)),
            component("text-input", "in1", "Name Filter", "\"key\": \"name\", \"value\": \"alpha\""),
            THE_QUERY,
            THE_TABLE);

    /// React's `LEGACY_DASHBOARD`: a dashboard saved before Stroom 7.2.0 (no model version), with its
    /// parameters in the deprecated `parameters` string.
    public static final String LEGACY_DASHBOARD = "{\"uuid\": \"dash-legacy\", \"type\": \"Dashboard\", "
            + "\"name\": \"Legacy Dashboard\", \"dashboardConfig\": {\"parameters\": \"user=jbloggs feed=MY_FEED\", "
            + "\"components\": [" + THE_QUERY + "], \"layout\": " + tabs(0, "q1") + "}}";

    /// React's `NO_DATASOURCE_DASHBOARD`: a Query with no data source that queries on open.
    public static final String NO_DATASOURCE_DASHBOARD = doc("dash-nds", "No Data Source", null, null,
            tabs(0, "q1"),
            component("query", "q1", "The Query", "\"automate\": {\"open\": true}"));

    /// React's `WINDOW_CLOSE_DASHBOARD`: a Query that queries on open, and its Table, in one tab layout.
    public static final String WINDOW_CLOSE_DASHBOARD = doc("dash-wc", "Window Close", null, null,
            tabs(0, "q1", "t1"),
            query("q1", "The Query", ", \"automate\": {\"open\": true}"),
            THE_TABLE);

    /// React's `SINGLE_GROUP_DASHBOARD`: [#THE_QUERY] and [#THE_TABLE] in one tab layout.
    public static final String SINGLE_GROUP_DASHBOARD = doc("dash-8", "One Panel", null, "\"designMode\": false",
            tabs(0, "q1", "t1"),
            THE_QUERY,
            THE_TABLE);

    /// React's `THREE_PANEL_DASHBOARD`: three panels side by side, the first with two text inputs.
    public static final String THREE_PANEL_DASHBOARD = doc("dash-9", "Three Panels", null, "\"designMode\": false",
            split(0, sized(tabs(0, "a", "drag"), 200, 100), sized(tabs(0, "b"), 200, 100),
                    sized(tabs(0, "cc"), 200, 100)),
            textInput("a", "A", "a"),
            textInput("drag", "Drag", "drag"),
            textInput("b", "B", "b"),
            textInput("cc", "C", "c"));

    /// React's `QUERY_ON_OPEN_DASHBOARD`: a Query that queries on open, and its Table.
    public static final String QUERY_ON_OPEN_DASHBOARD = doc("dash-10", "Auto Open", null, null,
            split(0, sized(tabs(0, "q1"), 300, 100), sized(tabs(0, "t1"), 300, 100)),
            query("q1", "Q", ", \"automate\": {\"open\": true}"),
            table("t1", "T", "q1", NAME_FIELD, ""));

    /// React's `SIZELESS_THREE_PANEL`: three text inputs side by side with no preferred sizes.
    public static final String SIZELESS_THREE_PANEL = doc("dash-11", "Sizeless Three", null, "\"designMode\": false",
            unsized(split(0, tabs(0, "p0"), tabs(0, "p1"), tabs(0, "p2"))),
            textInput("p0", "P0", "p0"),
            textInput("p1", "P1", "p1"),
            textInput("p2", "P2", "p2"));

    /// React's `QUERY_FIELDS_DASHBOARD`: a Query with the expression `Name = alpha`, and its Table.
    public static final String QUERY_FIELDS_DASHBOARD = doc("dash-12", "Query Fields", null, null,
            split(0, sized(tabs(0, "q1"), 400, 100), sized(tabs(0, "t1"), 300, 100)),
            query("q1", "The Query", ", \"expression\": " + operator(term("Name", "EQUALS", "alpha"))),
            THE_TABLE);

    /// React's `LIST_INPUT_DASHBOARD`: a List Input of the dictionary `Colours`, allowing free text.
    public static final String LIST_INPUT_DASHBOARD = listInput("dash-13", "List Input", "");

    /// React's `STALE_LIST_INPUT_DASHBOARD`: as [#LIST_INPUT_DASHBOARD], with the value `purple`,
    /// which isn't in the dictionary.
    public static final String STALE_LIST_INPUT_DASHBOARD = listInput("dash-13b", "Stale List Input",
            ", \"value\": \"purple\"");

    /// React's `TABLE_FILTER_DASHBOARD`: a Table Filter of the table's `Colour` column, a Query whose
    /// expression uses the filter's values, and the Table.
    public static final String TABLE_FILTER_DASHBOARD = doc("dash-14", "Table Filter", null, null,
            split(0, sized(tabs(0, "tf1"), 250, 100), sized(tabs(0, "q1"), 250, 100), sized(tabs(0, "t1"), 250, 100)),
            query("q1", "Q", ", \"expression\": "
                    + operator(term("Colour", "EQUALS", "${component.col-colour.values}"))),
            table("t1", "T", "q1", field("col-colour", "Colour") + ", " + field("f-m", "Match"), ""),
            component("table-filter", "tf1", "Filter", "\"tableId\": \"t1\", \"columns\": ["
                    + field("col-colour", "Colour") + "]"));

    /// React's `CONSTRAINED_DASHBOARD`: an Embedded Query in a dashboard 1200px wide (not fitting
    /// the width) that fits the height.
    public static final String CONSTRAINED_DASHBOARD = doc("dash-15", "Constrained", null,
            "\"layoutConstraints\": {\"fitWidth\": false, \"fitHeight\": true}, "
            + "\"preferredSize\": {\"width\": 1200, \"height\": 600}",
            tabs(0, "eq1"),
            component("embedded-query", "eq1", "Panel", "\"reference\": false, "
                    + "\"embeddedQueryDoc\": {\"query\": \"from index\\nselect x\\n\"}"));

    /// React's `QUERY_TABLE_VIS_SPLIT`: a Query, a Table and a Visualisation of the table side by side.
    public static final String QUERY_TABLE_VIS_SPLIT = doc("dash-5", "Q+T+V split", null, null,
            split(0, sized(tabs(0, "q1"), 250, 100), sized(tabs(0, "t1"), 350, 100), sized(tabs(0, "v1"), 350, 100)),
            query("q1", "Q", ""),
            table("t1", "T", "q1", NAME_FIELD, ""),
            vis("v1", "Chart"));

    /// React's `SELECTION_FILTER_DASHBOARD`: an Embedded Query with a selection filter `Status = OPEN`.
    public static final String SELECTION_FILTER_DASHBOARD = doc("dash-sf", "Selection Filter", null, null,
            tabs(0, "eq1"),
            component("embedded-query", "eq1", "Panel", "\"reference\": false, "
                    + "\"embeddedQueryDoc\": {\"query\": \"from index\\nselect name\\n\"}, "
                    + "\"selectionFilter\": [{\"id\": \"sf\", \"enabled\": true, \"expression\": "
                    + operator(term("Status", "EQUALS", "OPEN")) + "}]"));

    /// React's `VIS_DYNAMIC_DASHBOARD`: a Table (with `Name` and `Count`) and a Visualisation of it.
    public static final String VIS_DYNAMIC_DASHBOARD = doc("dash-16", "Vis Controls", null, "\"designMode\": false",
            split(0, sized(tabs(0, "t1"), 300, 100), sized(tabs(0, "v1"), 300, 100)),
            query("q1", "Q", ""),
            table("t1", "T", "q1", NAME_FIELD + ", " + field("f-count", "Count"), ""),
            vis("v1", "Chart"));

    /// React's `LINK_PARAM_DASHBOARD`: an Embedded Query whose query uses the link parameter `env`.
    public static final String LINK_PARAM_DASHBOARD = doc("dash-lp", "Link Params", null, null,
            tabs(0, "eq1"),
            component("embedded-query", "eq1", "Panel", "\"reference\": false, \"embeddedQueryDoc\": "
                    + "{\"query\": \"from index\\nwhere Environment = ${link.param.env}\\nselect name\\n\"}"));

    /// React's `PARAM_COLUMN_DASHBOARD`: a Query and a Table whose column's expression is
    /// `${link.param.env}`.
    public static final String PARAM_COLUMN_DASHBOARD = doc("dash-pc", "Param Column", null, null,
            tabs(0, "q1", "t1"),
            query("q1", "Q", ""),
            table("t1", "T", "q1", "{\"id\": \"f-name\", \"name\": \"Name\", \"expression\": \"${link.param.env}\"}",
                    ""));

    /// React's `CF_DASHBOARD`: a Query and a Table with a conditional formatting rule (`cf1`) giving
    /// matching rows a red background.
    public static final String CF_DASHBOARD = doc("dash-cf", "Conditional Formatting", null, null,
            tabs(0, "q1", "t1"),
            query("q1", "Q", ""),
            table("t1", "T", "q1", NAME_FIELD, ", \"conditionalFormattingRules\": [{\"id\": \"cf1\", "
                    + "\"enabled\": true, \"formattingType\": \"BACKGROUND\", \"formattingStyle\": \"RED\", "
                    + "\"expression\": {\"type\": \"operator\", \"op\": \"AND\", \"children\": []}}]"));

    /// React's `CF_CUSTOM_DASHBOARD`: a Query and a Table with a custom conditional formatting rule
    /// (`cfc`) with different light and dark background colours.
    public static final String CF_CUSTOM_DASHBOARD = doc("dash-cfc", "CF Custom", null, null,
            tabs(0, "q1", "t1"),
            query("q1", "Q", ""),
            table("t1", "T", "q1", NAME_FIELD, ", \"conditionalFormattingRules\": [{\"id\": \"cfc\", "
                    + "\"enabled\": true, \"formattingType\": \"CUSTOM\", \"customStyle\": "
                    + "{\"light\": {\"backgroundColour\": \"rgb(10, 20, 30)\"}, "
                    + "\"dark\": {\"backgroundColour\": \"rgb(200, 100, 50)\"}}, "
                    + "\"expression\": {\"type\": \"operator\", \"op\": \"AND\", \"children\": []}}]"));

    /// React's `HIDDEN_TAB_DASHBOARD`: three Text components in one tab layout, the first hidden and
    /// the third (by its index in the whole list) selected.
    public static final String HIDDEN_TAB_DASHBOARD = doc("dash-hidden", "Hidden Tab", null, null,
            "{\"type\": \"tabLayout\", \"tabs\": [{\"id\": \"a1\", \"visible\": false}, "
            + "{\"id\": \"b1\", \"visible\": true}, {\"id\": \"c1\", \"visible\": true}], \"selected\": 2"
            + DEFAULT_SIZE + "}",
            component("text", "a1", "Alpha", ""),
            component("text", "b1", "Bravo", ""),
            component("text", "c1", "Charlie", ""));

    private DashboardDocs() {
        // Static utility
    }

    /// A dashboard document.
    ///
    /// @param uuid       Its UUID.
    /// @param name       Its name.
    /// @param docMembers More members of the document (e.g. its description), or null.
    /// @param config     More members of its configuration (e.g. `designMode`), or null.
    /// @param layout     The JSON of its layout ([#tabs], [#split]).
    /// @param components The JSON of its components.
    /// @return The document's JSON.
    public static String doc(final String uuid,
                             final String name,
                             final String docMembers,
                             final String config,
                             final String layout,
                             final String... components) {
        return "{\"uuid\": \"" + uuid + "\", \"type\": \"Dashboard\", \"name\": \"" + name + "\", "
               + (docMembers != null
                ? docMembers + ", "
                : "")
               + "\"dashboardConfig\": {"
               + (config != null
                ? config + ", "
                : "")
               + "\"modelVersion\": \"7.2.0\", \"components\": [" + String.join(", ", components)
               + "], \"layout\": " + layout + "}}";
    }

    /// A component of a dashboard.
    ///
    /// @param type     Its type, e.g. `query`.
    /// @param id       Its id.
    /// @param name     Its name (its tab's label).
    /// @param settings The members of its settings (other than their `type`).
    /// @return The component's JSON.
    public static String component(final String type, final String id, final String name, final String settings) {
        // Differs from React: an Embedded Query's embedded query document needs its type, UUID and
        // name (as every document, which Stroom's server gives it); React's leave them out. Its
        // settings may leave out 'automate' and 'queryTablePreferences', as React's do
        // (EmbeddedQueryPresenter once read them without null checks)
        final String allSettings = settings.replace("\"embeddedQueryDoc\": {\"query\"",
                "\"embeddedQueryDoc\": {\"type\": \"Query\", \"uuid\": \"eq-" + id + "\", \"name\": \""
                + name + "\", \"query\"");
        return "{\"type\": \"" + type + "\", \"id\": \"" + id + "\", \"name\": \"" + name
               + "\", \"settings\": {\"type\": \"" + type + "\"" + (allSettings.isEmpty()
                ? ""
                : ", " + allSettings) + "}}";
    }

    /// A Query component searching [#INDEX].
    ///
    /// @param id    Its id.
    /// @param name  Its name.
    /// @param extra More members of its settings, starting with a comma, or empty.
    /// @return The component's JSON.
    public static String query(final String id, final String name, final String extra) {
        return component("query", id, name, "\"dataSource\": " + INDEX + extra);
    }

    /// A Table component.
    ///
    /// @param id      Its id.
    /// @param name    Its name.
    /// @param queryId The id of its Query component.
    /// @param fields  The JSON of its columns (without the brackets).
    /// @param extra   More members of its settings, starting with a comma, or empty.
    /// @return The component's JSON.
    public static String table(final String id,
                               final String name,
                               final String queryId,
                               final String fields,
                               final String extra) {
        return component("table", id, name, "\"queryId\": \"" + queryId + "\", \"fields\": [" + fields + "]" + extra);
    }

    /// A column of a table.
    ///
    /// @param id   Its id.
    /// @param name Its name.
    /// @return The column's JSON.
    public static String field(final String id, final String name) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + name + "\"}";
    }

    /// A Visualisation component (the visualisation `Bar`, `vis-1`) of the table `t1`.
    ///
    /// @param id   Its id.
    /// @param name Its name.
    /// @return The component's JSON.
    public static String vis(final String id, final String name) {
        return component("vis", id, name, "\"tableId\": \"t1\", \"visualisation\": {\"type\": \"Visualisation\", "
                                          + "\"uuid\": \"vis-1\", \"name\": \"Bar\"}, \"json\": \"{}\"");
    }

    /// A Text Input component.
    ///
    /// @param id   Its id.
    /// @param name Its name.
    /// @param key  Its parameter's key.
    /// @return The component's JSON.
    public static String textInput(final String id, final String name, final String key) {
        return component("text-input", id, name, "\"key\": \"" + key + "\"");
    }

    /// A tab layout, with the default preferred size (see [#sized]).
    ///
    /// @param selected The index of its selected tab.
    /// @param ids      The ids of its components (all visible).
    /// @return The layout's JSON.
    public static String tabs(final int selected, final String... ids) {
        final StringBuilder sb = new StringBuilder("{\"type\": \"tabLayout\", \"tabs\": [");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"id\": \"").append(ids[i]).append("\", \"visible\": true}");
        }
        return sb.append("], \"selected\": ").append(selected).append(DEFAULT_SIZE).append("}").toString();
    }

    /// A layout with a preferred size in place of its default one.
    ///
    /// @param layout The layout's JSON.
    /// @param width  Its preferred width.
    /// @param height Its preferred height.
    /// @return The layout's JSON with its preferred size.
    public static String sized(final String layout, final int width, final int height) {
        return layout.substring(0, layout.lastIndexOf(", \"preferredSize\": ")) + ", \"preferredSize\": {\"width\": "
               + width + ", \"height\": " + height + "}}";
    }

    /// A layout and its children with no preferred sizes.
    ///
    /// @param layout The layout's JSON.
    /// @return The layout's JSON with every preferred size removed.
    public static String unsized(final String layout) {
        return layout.replace(DEFAULT_SIZE, "");
    }

    /// A split layout, with the default preferred size (see [#sized]).
    ///
    /// @param dimension The dimension it splits (0 across, 1 down).
    /// @param children  The JSON of its children.
    /// @return The layout's JSON.
    public static String split(final int dimension, final String... children) {
        return "{\"type\": \"splitLayout\", \"dimension\": " + dimension + ", \"children\": ["
               + String.join(", ", children) + "]" + DEFAULT_SIZE + "}";
    }

    /// An expression with one term, `AND`ed.
    ///
    /// @param terms The JSON of its terms.
    /// @return The expression's JSON.
    public static String operator(final String... terms) {
        return "{\"type\": \"operator\", \"op\": \"AND\", \"children\": [" + String.join(", ", terms) + "]}";
    }

    /// A term of an expression.
    ///
    /// @param field     Its field.
    /// @param condition Its condition, e.g. `EQUALS`.
    /// @param value     Its value.
    /// @return The term's JSON.
    public static String term(final String field, final String condition, final String value) {
        return "{\"type\": \"term\", \"field\": \"" + field + "\", \"condition\": \"" + condition
               + "\", \"value\": \"" + value + "\"}";
    }

    // React's Query + Table + Text dashboards, with more settings of the Text component
    private static String queryTableText(final String uuid, final String textSettings) {
        return doc(uuid, "Query + Table + Text", null, "\"designMode\": false",
                split(0, sized(tabs(0, "q1"), 300, 100), sized(tabs(0, "t1"), 300, 100),
                        sized(tabs(0, "x1"), 300, 100)),
                query("q1", "Q", ""),
                table("t1", "T", "q1", NAME_FIELD + ", " + field("f-stream", "StreamId") + ", "
                                       + field("f-event", "EventId"), ""),
                component("text", "x1", "Source", "\"tableId\": \"t1\", \"streamIdField\": {\"name\": \"StreamId\"}, "
                                                  + "\"recordNoField\": {\"name\": \"EventId\"}" + textSettings));
    }

    // React's List Input dashboards
    private static String listInput(final String uuid, final String name, final String extra) {
        return doc(uuid, name, null, null,
                tabs(0, "li1"),
                component("list-input", "li1", "Colour", "\"key\": \"colour\", \"useDictionary\": true, "
                        + "\"allowTextEntry\": true, \"dictionary\": {\"type\": \"Dictionary\", \"uuid\": \"d1\", "
                        + "\"name\": \"Colours\"}" + extra));
    }
}
