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

import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.ArrayList;
import java.util.List;

/// The searching stories of `App/Editors/DashboardEditor` (see `DashboardEditorStories`): a
/// dashboard's Query, Embedded Query and input components running Stroom's real searches
/// (`SearchModel`, `QueryModel`) against fake replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /dashboard/v1/search/{node}` | a Query component's search |
/// | `POST /query/v1/search/{node}` | an Embedded Query's search |
/// | `POST /result-store/v1/destroy/{node}` | destroying a search's results |
/// | `POST /dataSource/v1/findFields` | a data source's fields |
/// | `GET /wordList/v1/{dictionaryUuid}` | a dictionary's words |
public final class DashboardSearchStories {

    private DashboardSearchStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(DashboardSupport.TITLE, DashboardSearchStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A query with no data source that queries on open warns, without a click
                .story("NoDataSourceWarnsOnAutoRun", DashboardSupport.story(DashboardDocs.NO_DATASOURCE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.findByText("The Query", DashboardPlays.COMPONENT_TAB);
                    // The warning is Stroom's alert dialog
                    play.screen().findByText("No data source has been chosen to search");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("WARN: No data source has been chosen to search");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Closing the window destroys the search's result store, saying why
                .story("WindowCloseDestroysStore", DashboardSupport.story(DashboardDocs.WINDOW_CLOSE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    // The table is the panel's second tab, which Stroom only shows when it is
                    // selected, so the play waits for the search's reply instead
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            DashboardSupport.SEARCH.toSpyMatcher()));
                    DashboardPlays.closeWindow(play);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/result-store/v1/destroy/" + RequestMatcher.PATH_WILDCARD)
                                    .withJsonBodyContaining("{\"destroyReason\": \"WINDOW_CLOSE\"}")
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // The embedded query runs its search and shows the results
                .story("RunEmbeddedQuery", DashboardSupport.story(DashboardDocs.DASHBOARD_DOC, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runAll(play);
                    play.waitFor(() -> play.expect(DashboardPlays.header(play, "Name")).toBeInTheDocument());
                    play.expect(play.getByText("alpha")).toBeInTheDocument();
                    play.expect(play.getByText("beta")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // Running the Query component's search gives the linked table its result
                .story("QueryDrivesTable", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.expect(DashboardPlays.tab(play, "The Query")).toBeInTheDocument();
                    play.expect(DashboardPlays.tab(play, "The Table")).toBeInTheDocument();
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(DashboardPlays.header(play, "Name")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.expect(play.getByText("beta")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // A query that queries on open runs without a click
                .story("QueryOnOpen", DashboardSupport.story(DashboardDocs.QUERY_ON_OPEN_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.expect(play.getByText("beta")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // A saved expression shows without the data source's fields being fetched
                .story("QueryFieldsLoadOnDemand", DashboardSupport.story(DashboardDocs.QUERY_FIELDS_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(play.getByText("Name = alpha")).toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/dataSource/v1/findFields").toSpyMatcher());
                    DashboardPlays.expectNoProblems(play);
                })
                // The dashboard toolbar's Execute Query runs every query
                .story("RunAllQueries", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runAll(play);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            DashboardSupport.SEARCH
                                    .withBody("asks for t1's results",
                                            body -> DashboardPlays.componentIds(body).contains("t1"))
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // While a query runs, the toolbar's toggle stops every query
                .story("RunStopToggle", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH,
                                RestReply.json(QueryFixtures.response(false, QueryFixtures.tableResult("t1",
                                        "[" + DashboardDocs.NAME_FIELD + "]",
                                        "[{\"values\": [\"alpha\"], \"depth\": 0}]", 1))),
                                // Later polls hang, so the search keeps running until it is stopped
                                RestReply.json(DashboardSupport.alphaBetaResponse("t1")).delayed(600_000))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runAll(play);
                    play.waitFor(() -> play.expect(DashboardPlays.toolbarButton(play, "Stop Query"))
                            .toBeInTheDocument());
                    play.click(DashboardPlays.toolbarButton(play, "Stop Query"));
                    play.waitFor(() -> play.expect(DashboardPlays.toolbarButton(play, "Execute Query"))
                            .toBeInTheDocument());
                    DashboardPlays.expectNoProblems(play);
                })
                // The parameters of the link that opened the dashboard are sent with its searches
                .story("LinkParams", DashboardSupport.story(DashboardDocs.LINK_PARAM_DASHBOARD,
                        routes -> routes.route(QueryFixtures.SEARCH, RestReply.json(QueryFixtures.response(true))),
                        options -> options.linkParams("env=prod other=x")))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runAll(play);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            QueryFixtures.SEARCH
                                    .withBody("has the link's params", body ->
                                            "prod".equals(DashboardPlays.param(body, "env"))
                                            && "x".equals(DashboardPlays.param(body, "other")))
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // An Embedded Query's selection filter is sent as its table's aggregate filter
                .story("EmbeddedQuerySelectionFilter", DashboardSupport.story(DashboardDocs.SELECTION_FILTER_DASHBOARD,
                        routes -> routes.route(QueryFixtures.SEARCH, RestReply.json(QueryFixtures.response(true)))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runAll(play);
                    // The static filter (Status = OPEN) is sent with the first search (it was once
                    // only applied when the dashboard's context changed after the table existed)
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            QueryFixtures.SEARCH
                                    .withBody("the selection filter Status = OPEN", body -> body != null
                                            && body.contains("\"selectionFilter\":{")
                                            && body.contains("\"field\":\"Status\"")
                                            && body.contains("\"value\":\"OPEN\""))
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // A Text Input's value is a parameter of the searches
                .story("InputDrivesQuery", DashboardSupport.story(DashboardDocs.INPUT_DRIVEN_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, request -> {
                            final String name = DashboardPlays.param(request.getBody(), "name");
                            return RestReply.json(DashboardSupport.tableResponse("t1", "[" + DashboardDocs.NAME_FIELD
                                                                                       + "]",
                                    name != null
                                            ? "[{\"values\": [\"param name = " + name + "\"], \"depth\": 0}]"
                                            : "[]",
                                    name != null
                                            ? 1
                                            : 0));
                        })))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.expect(DashboardPlays.tab(play, "Name Filter")).toBeInTheDocument();
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("param name = alpha")).toBeInTheDocument());
                    // A plain parameter change applies to the next search
                    play.clear(play.querySelector(".TextInputView input"));
                    play.type(play.querySelector(".TextInputView input"), "omega");
                    // The text box reports its value when it loses the focus
                    play.tab();
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("param name = omega")).toBeInTheDocument());
                    DashboardPlays.expectNoProblems(play);
                })
                // Selecting a master table's row re-runs the detail query with the selection
                .story("SelectionDrivesQuery", DashboardSupport.story(DashboardDocs.SELECTION_DRIVEN_DASHBOARD,
                        DashboardSearchStories::selectionDrivenRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.expect(DashboardPlays.tab(play, "Master Table")).toBeInTheDocument();
                    play.expect(DashboardPlays.tab(play, "Detail Table")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.QUERY_RUN_BUTTON))
                            .toHaveLength(2));
                    runMaster(play);
                    play.expect(play.queryByText("detail for alpha")).toBeNull();
                    play.click(play.getByText("alpha"));
                    play.waitFor(() -> play.expect(play.getByText("detail for alpha")).toBeInTheDocument());
                    expectSearchesComplete(play);
                    DashboardPlays.expectNoProblems(play);
                })
                // The current selection's dialog lists the dashboard's parameters and selections
                .story("CurrentSelectionPopup", DashboardSupport.story(DashboardDocs.SELECTION_DRIVEN_DASHBOARD,
                        DashboardSearchStories::selectionDrivenRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.QUERY_RUN_BUTTON))
                            .toHaveLength(2));
                    runMaster(play);
                    play.click(play.getByText("alpha"));
                    // Not offered outside design mode
                    play.expect(play.queryByRole("button", "View Current Selection")).toBeNull();
                    DashboardPlays.designMode(play, true);
                    play.click(play.getByRole("button", "View Current Selection"));
                    final Play dialog = DashboardPlays.dialog(screen, "Current Selection");
                    play.expect(dialog.getByText("Time Range")).toBeInTheDocument();
                    play.expect(dialog.getByText("${timeRange.from}")).toBeInTheDocument();
                    // Stroom heads a component's block with its name and id
                    play.expect(dialog.getByText("Master Table (t1)")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(dialog.getByText("${component.t1.selection.Name}"))
                            .toBeInTheDocument());
                    play.expect(dialog.getAllByText("alpha").count()).toBeGreaterThan(0);
                    DashboardPlays.expectNoProblems(play);
                })
                // A Table Filter's values are expanded in a query's expression
                .story("TableFilterDrivesQuery", DashboardSupport.story(DashboardDocs.TABLE_FILTER_DASHBOARD,
                        DashboardSearchStories::tableFilterRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Play screen = play.screen();
                    // The Table Filter is a list of the column's values to tick
                    // (ColumnValuesFilterPresenter), so the play chooses 'red' and 'green' there;
                    // the values are the table's, so the query is run first
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("red")).toBeInTheDocument());
                    play.click(play.getByText("Select None"));
                    play.click(play.getByText("red"));
                    play.click(play.getByText("green"));
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("matched red")).toBeInTheDocument());
                    play.expect(screen.queryByText(TextMatch.containing("Error"))).toBeNull();
                    DashboardPlays.expectNoProblems(play);
                })
                // A List Input's choices are the words of its dictionary
                .story("ListInputDictionary", DashboardSupport.story(DashboardDocs.LIST_INPUT_DASHBOARD,
                        DashboardSearchStories::wordListRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Play screen = play.screen();
                    // The words are the choices of Stroom's selection box (shown when it is opened)
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get("/wordList/v1/d1").toSpyMatcher()));
                    // A selection box that allows free text opens with its drop down button
                    play.click(play.querySelector(".ListInputView .svgIconBox-icon-outer"));
                    screen.findByText("red", ".SelectionPopup *");
                    play.expect(screen.getByText("blue", ".SelectionPopup *")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // A List Input's value that isn't one of its dictionary's words is cleared, but
                // free text typed into it is kept
                .story("ListInputDictionaryResolvesStaleValue", DashboardSupport.story(
                        DashboardDocs.STALE_LIST_INPUT_DASHBOARD, DashboardSearchStories::wordListRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get("/wordList/v1/d1").toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.querySelector(".ListInputView " + StroomDom.SELECTION_BOX))
                            .toHaveValue(""));
                    play.type(play.querySelector(".ListInputView " + StroomDom.SELECTION_BOX), "teal");
                    play.waitFor(() -> play.expect(play.querySelector(".ListInputView " + StroomDom.SELECTION_BOX))
                            .toHaveValue("teal"));
                    DashboardPlays.expectNoProblems(play);
                })
                // A table's column expressions have the dashboard's parameters replaced before they
                // are sent
                .story("TableColumnParamSubstitution", DashboardSupport.story(DashboardDocs.PARAM_COLUMN_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, RestReply.json(
                                DashboardSupport.tableResponse("t1", "[" + DashboardDocs.NAME_FIELD + "]",
                                        DashboardSupport.ALPHA_BETA_ROWS, 500))),
                        options -> options.linkParams("env=prod")))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            DashboardSupport.SEARCH
                                    .withBody("resolves t1's column expression", body -> "prod".equals(
                                            DashboardPlays.at(DashboardPlays.componentRequest(body, "t1"),
                                                    "tableSettings", "fields", 0, "expression")))
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // A new search asks for the first page of a linked table that was paged forward
                .story("LinkedTableResetsOnNewSearch", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, RestReply.json(
                                DashboardSupport.tableResponse("t1", "[" + DashboardDocs.NAME_FIELD + "]",
                                        DashboardSupport.ALPHA_BETA_ROWS, 500)))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    // Only the table has a pager here (the Query has none)
                    play.click(play.within(play.querySelector(".TableViewImpl")).getByTitle("Forward"));
                    play.waitFor(() -> play.expect("a request for a later page of t1",
                            () -> offsets(requests).stream().anyMatch(offset -> offset > 0)).toBe(true));
                    final Value<Integer> before = play.capture("before", requests.callCount());
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect("t1's requests after the new search",
                            () -> offsets(requests.getCalls().subList(before.get(), requests.getCallCount())))
                            .not().toHaveLength(0));
                    play.expect("t1's offsets after the new search",
                                    () -> offsets(requests.getCalls().subList(before.get(), requests.getCallCount()))
                                            .stream()
                                            .allMatch(offset -> offset == 0))
                            .toBe(true);
                    DashboardPlays.expectNoProblems(play);
                });
    }

    // Runs only the master query (q1), while the detail query (q2) hasn't searched, and checks that
    // its search completes. DashboardPresenter.getCombinedErrors asks every Query for its errors as
    // any search reports them, and QueryPresenter.getCurrentErrors once threw for a Query that
    // hadn't searched, which SearchModel.update swallowed before it marked the search complete, so
    // the master search polled forever
    private static void runMaster(final Play play) {
        DashboardPlays.runQuery(play, 0);
        play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
        expectSearchesComplete(play);
    }

    // No Query is still searching: each Query's own button is back to 'Execute Query' (it is
    // 'Stop Query', with the 'stop' class, while its search polls)
    private static void expectSearchesComplete(final Play play) {
        play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.QUERY_RUN_BUTTON + ".stop"))
                .toHaveLength(0));
        play.expect(play.querySelectorAll(DashboardPlays.QUERY_RUN_BUTTON + "[title='Execute Query']"))
                .toHaveLength(2);
    }

    // The offsets of t1's result requests in the dashboard searches the spy recorded
    private static List<Long> offsets(final Spy requests) {
        return offsets(requests.getCalls());
    }

    private static List<Long> offsets(final List<List<Object>> calls) {
        final List<Long> offsets = new ArrayList<>();
        for (final List<Object> call : calls) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if (DashboardSupport.SEARCH.matches(request)) {
                final Object t1 = DashboardPlays.componentRequest(request.getBody(), "t1");
                if (t1 != null) {
                    final Object offset = DashboardPlays.at(t1, "requestedRange", "offset");
                    offsets.add(offset instanceof final Number number
                            ? number.longValue()
                            : 0L);
                }
            }
        }
        return offsets;
    }

    // Selection driven searches: t1 has two rows; t2 has a row for the selection value in the
    // request's expression
    private static void selectionDrivenRoutes(final RestFixtures.Builder routes) {
        routes.route(DashboardSupport.SEARCH, request -> {
            final String body = request.getBody();
            final List<String> ids = DashboardPlays.componentIds(body);
            final StringBuilder results = new StringBuilder();
            if (ids.contains("t1")) {
                results.append(QueryFixtures.tableResult("t1", "[" + DashboardDocs.NAME_FIELD + "]",
                        DashboardSupport.ALPHA_BETA_ROWS, 2));
            }
            if (ids.contains("t2")) {
                final String match = DashboardPlays.firstTermValue(DashboardPlays.at(body, "search", "expression"));
                if (results.length() > 0) {
                    results.append(", ");
                }
                results.append(QueryFixtures.tableResult("t2", "[" + DashboardDocs.field("f-detail", "Detail") + "]",
                        match != null
                                ? "[{\"values\": [\"detail for " + match + "\"], \"depth\": 0}]"
                                : "[]",
                        match != null
                                ? 1
                                : 0));
            }
            return RestReply.json(QueryFixtures.response(true, results.toString()));
        });
    }

    // Filter echoing searches: t1 has a row for the first term's value of the request's expression;
    // and the Colour column's values for the Table Filter
    private static void tableFilterRoutes(final RestFixtures.Builder routes) {
        routes.route(DashboardSupport.SEARCH, request -> {
            final String match = DashboardPlays.firstTermValue(DashboardPlays.at(request.getBody(),
                    "search", "expression"));
            final String value = match == null || match.startsWith("${")
                    ? null
                    : match;
            return RestReply.json(DashboardSupport.tableResponse("t1", "[" + DashboardDocs.field("f-m", "Match") + "]",
                    value != null
                            ? "[{\"values\": [\"matched " + value + "\"], \"depth\": 0}]"
                            : "[]",
                    value != null
                            ? 1
                            : 0));
        });
        routes.route(RequestMatcher.post("/dashboard/v1/columnValues/" + RequestMatcher.PATH_WILDCARD),
                RestReply.json("{\"values\": [{\"value\": \"red\"}, {\"value\": \"green\"}, {\"value\": \"blue\"}], "
                               + "\"pageResponse\": {\"offset\": 0, \"length\": 3, \"total\": 3, \"exact\": true}}"));
    }

    // WordListResource: the dictionary's words
    private static void wordListRoutes(final RestFixtures.Builder routes) {
        routes.get("/wordList/v1/d1", RestReply.json("{\"wordList\": [{\"word\": \"red\", \"sourceUuid\": \"d1\"}, "
                                                     + "{\"word\": \"green\", \"sourceUuid\": \"d1\"}, "
                                                     + "{\"word\": \"blue\", \"sourceUuid\": \"d1\"}], "
                                                     + "\"sourceUuidToDocRefMap\": {\"d1\": {\"type\": \"Dictionary\", "
                                                     + "\"uuid\": \"d1\", \"name\": \"Colours\"}}}"));
    }
}
