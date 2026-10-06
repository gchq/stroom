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

package stroom.gwt.workbench.client.app.query;

import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;

/// Fixtures for Stroom's searches (StroomQL queries, analytic rules and reports), shared by the
/// query batch's stories and reusable by the dashboard's, as plain JSON in Stroom's shapes.
///
/// A search is a poll: `QueryModel` (and the dashboard's `SearchModel`) posts the search request
/// to `POST /query/v1/search/{node}` (`/dashboard/v1/search/{node}` for a dashboard), the first
/// time with the node `null` and no query key, and posts it again at once with the node and key of
/// the reply until a reply is `complete` (or `null`, which also ends the search). So a search's
/// replies are a sequence ending with a complete reply (the last reply repeats), e.g.
/// `.route(QueryFixtures.SEARCH, RestReply.json(QueryFixtures.response(false, TABLE)),
/// RestReply.json(QueryFixtures.response(true, TABLE)))`. A new search (or leaving the screen)
/// destroys the previous result store with `POST /result-store/v1/destroy/{node}`, which
/// [#resultStoreRoutes] answers.
public final class QueryFixtures {

    /// Every `POST /query/v1/search/{node}` request, whatever the node.
    public static final RequestMatcher SEARCH = RequestMatcher.post("/query/v1/search/" + RequestMatcher.PATH_WILDCARD);

    /// The path of `QueryResource.fetchQueryHelpItems()`.
    public static final String HELP_ITEMS_PATH = "/query/v1/helpItems";

    /// The node that the replies say ran the search.
    public static final String NODE = "node1";

    /// The query key that the replies give the search.
    public static final String QUERY_KEY = "qk-1";

    // DashboardSearchResponse
    private static final String RESPONSE = """
            {"complete": COMPLETE, "node": "NODE", "queryKey": {"uuid": "QUERY_KEY"}, "highlights": [],
              "errorMessages": [], "results": [RESULTS]}""";

    // The defaults of editorRoutes
    private static final RestFixtures EDITOR_DEFAULTS = resultStoreRoutes(RestFixtures.builder())
            .route(RequestMatcher.post(HELP_ITEMS_PATH), RestReply.json(helpItems()))
            .route(RequestMatcher.get("/query/v1/fetchTimeZones"), RestReply.json("[\"UTC\"]"))
            .route(RequestMatcher.post("/query/v1/fetchDataSourceFromQueryString"),
                    RestReply.json("{\"type\": \"Index\", \"uuid\": \"idx-1\", \"name\": \"index\"}"))
            .route(RequestMatcher.get("/activity/v1/current"), RestReply.noContent())
            .route(RequestMatcher.post("/query/v1/fetchDetail"), RestReply.json(
                    "{\"documentation\": \"\", \"insertType\": \"NOT_INSERTABLE\"}"))
            .build();

    private QueryFixtures() {
        // Static utility
    }

    /// A search reply (`DashboardSearchResponse`) from [#NODE] for the query key [#QUERY_KEY].
    ///
    /// @param complete Whether the search is complete (stopping the poll).
    /// @param results  The JSON of each component's result, e.g. [#tableResult].
    /// @return The reply's JSON.
    public static String response(final boolean complete, final String... results) {
        return RESPONSE.replace("COMPLETE", String.valueOf(complete))
                .replace("NODE", NODE)
                .replace("QUERY_KEY", QUERY_KEY)
                .replace("RESULTS", String.join(", ", results));
    }

    /// A table component's result (`TableResult`, type `table`).
    ///
    /// @param componentId The component's id (`table` for a query's table).
    /// @param fields      The JSON array of the table's columns (`Column`s).
    /// @param rows        The JSON array of its rows (`Row`s).
    /// @param totalRows   The total number of rows.
    /// @return The result's JSON.
    public static String tableResult(final String componentId,
                                     final String fields,
                                     final String rows,
                                     final int totalRows) {
        return "{\"type\": \"table\", \"componentId\": \"" + componentId + "\", \"fields\": " + fields
               + ", \"rows\": " + rows + ", \"resultRange\": {\"offset\": 0, \"length\": " + totalRows
               + "}, \"totalResults\": " + totalRows + "}";
    }

    /// A StroomQL visualisation's result (`QLVisResult`, type `ql_vis`).
    ///
    /// @param componentId   The component's id (`vis` for a query's visualisation).
    /// @param visSettings   The JSON of the visualisation's settings (`QLVisSettings`).
    /// @param jsonData      The data, as a JSON string's content (already escaped), or empty.
    /// @return The result's JSON.
    public static String visResult(final String componentId, final String visSettings, final String jsonData) {
        return "{\"type\": \"ql_vis\", \"componentId\": \"" + componentId + "\", \"visSettings\": " + visSettings
               + ", \"jsonData\": \"" + jsonData + "\"}";
    }

    /// Adds the routes of the requests a StroomQL query editor (`QueryEditPresenter`, in a Query,
    /// an analytic rule or a report) makes as it opens and before it searches, with defaults: the
    /// query help's items (`POST /query/v1/helpItems`, none), the time zones
    /// (`GET /query/v1/fetchTimeZones`, `UTC`), the query's data source
    /// (`POST /query/v1/fetchDataSourceFromQueryString`, the index `index`), the user's current
    /// activity (`GET /activity/v1/current`, none, which Stroom checks before each search) and a
    /// selected help item's details (`POST /query/v1/fetchDetail`, no documentation, not
    /// insertable). Add them after the story's own routes for these, which then take precedence.
    ///
    /// @param builder The story's fixtures.
    /// @return The builder.
    public static RestFixtures.Builder editorRoutes(final RestFixtures.Builder builder) {
        // Added with addAll, so that a story's own route for one of these comes first and wins
        return builder.addAll(EDITOR_DEFAULTS);
    }

    /// The default UI config (`StartupFixtures.DEFAULT_UI_CONFIG`) with more members, which
    /// replace any default member of the same name (JSON's last member wins), e.g. for the
    /// `analyticUiDefaultConfig` or `query` settings of a story, for
    /// `ScreenHarness.Builder.uiConfig(...)`.
    ///
    /// @param members The JSON members to add, e.g. `"query": {...}`.
    /// @return The UI config's JSON.
    public static String uiConfigWith(final String members) {
        final String defaults = StartupFixtures.DEFAULT_UI_CONFIG.trim();
        return defaults.substring(0, defaults.lastIndexOf('}')) + ", " + members + "}";
    }

    /// A page of query help items (`ResultPage<QueryHelpRow>`).
    ///
    /// @param rows The JSON of each row (`QueryHelpRow`), e.g. [#helpRow].
    /// @return The page's JSON.
    public static String helpItems(final String... rows) {
        return "{\"values\": [" + String.join(", ", rows) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
               + rows.length + ", \"total\": " + rows.length + ", \"exact\": true}}";
    }

    /// A query help item (`QueryHelpRow`).
    ///
    /// @param type        Its type, e.g. `TITLE`, `FUNCTION`, `FIELD`.
    /// @param id          Its id (its children are asked for with the parent path `id.`).
    /// @param title       Its title.
    /// @param hasChildren Whether it has children.
    /// @return The row's JSON.
    public static String helpRow(final String type, final String id, final String title, final boolean hasChildren) {
        return "{\"type\": \"" + type + "\", \"id\": \"" + id + "\", \"title\": \"" + title
               + "\", \"hasChildren\": " + hasChildren + "}";
    }

    /// Adds the routes of the result store requests a search makes when it is replaced or
    /// stopped (`destroy` and `terminate`), which reply `true`.
    ///
    /// @param builder The story's fixtures.
    /// @return The builder.
    public static RestFixtures.Builder resultStoreRoutes(final RestFixtures.Builder builder) {
        return builder
                .route(RequestMatcher.post("/result-store/v1/destroy/" + RequestMatcher.PATH_WILDCARD),
                        RestReply.json("true"))
                .route(RequestMatcher.post("/result-store/v1/terminate/" + RequestMatcher.PATH_WILDCARD),
                        RestReply.json("true"));
    }
}
