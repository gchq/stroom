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

package stroom.gwt.workbench.client.app.editors;

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.client.CreateAnnotationEvent;
import stroom.annotation.client.EditAnnotationEvent;
import stroom.annotation.client.FindAnnotationPresenter;
import stroom.annotation.client.ShowFindAnnotationEvent;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.gwt.workbench.client.app.ai.AiFixtures;
import stroom.gwt.workbench.client.app.ai.AskStroomAiChat;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.BiConsumer;

/// The `App/Editors/QueryEditor` stories, showing Stroom's real `QueryDocPresenter` (a Query's
/// editor tab: Query, Documentation and Permissions, the Query tab being the query help, the
/// StroomQL editor and the results table) with fake REST replies.
///
/// As `QueryPlugin` does, the story fetches the document (`GET /query/v1/{uuid}`), checks the user
/// may edit it and reads it into the editor. The other routes:
///
/// | Stroom endpoint                                       | Used for                        |
/// |-------------------------------------------------------|---------------------------------|
/// | `POST /query/v1/search/{node}`, polled to completion  | a search                        |
/// | `POST /query/v1/helpItems` / `fetchDetail`            | the query help                  |
/// | `POST /query/v1/fetchDataSourceFromQueryString`       | the query's data source         |
/// | `GET /query/v1/fetchTimeZones`                        | the time zones                  |
/// | `POST /query/v1/downloadSearchResults/{node}`         | Download                        |
/// | `POST /query/v1/columnValues/{node}`                  | a column's values filter        |
/// | `POST /result-store/v1/destroy/{node}`, `terminate`   | ending a search                 |
/// | `POST /data/v1/fetch`                                 | a row's source                  |
/// | the Permissions tab's `fetchDocumentUserPermissions`  | the Permissions tab             |
///
/// Stroom also asks for the user's current activity (`GET /activity/v1/current`) before each
/// search. The plays check the requests the screen made (`REQUEST_SPY`). The shared search
/// fixtures are in [QueryFixtures].
public final class QueryEditorStories {

    private static final DocRef DOC_REF = new DocRef(QueryDoc.TYPE, "query-1", "My Query");

    // QueryResource.fetch()
    private static final String DOC = """
            {
              "type": "Query", "uuid": "query-1", "name": "My Query",
              "query": "from index\\nselect name, count\\n",
              "timeRange": {"name": "All time"},
              "description": "# Query docs"
            }""";

    // FindAnnotationPresenter's warning for OK with no annotation selected
    private static final String NOTHING_SELECTED = "No annotation has been selected";

    // One table result with four columns and two rows, the second annotated
    static final String TABLE = QueryFixtures.tableResult("table", """
                    [{"id": "f-name", "name": "Name", "visible": true},
                     {"id": "f-count", "name": "Count", "format": {"type": "NUMBER"}, "visible": true},
                     {"id": "f-stream", "name": "StreamId", "visible": true},
                     {"id": "f-event", "name": "EventId", "visible": true}]""", """
                    [{"values": ["alpha", "10", "1001", "5"], "depth": 0},
                     {"values": ["beta", "20", "1002", "7"], "depth": 0, "annotationId": 55}]""",
            2);

    private static final String COMPLETE = QueryFixtures.response(true, TABLE);

    // The help items: two titles at the root, each with one child
    private static final String HELP_ROOT = QueryFixtures.helpItems(
            QueryFixtures.helpRow("TITLE", "functions", "Functions", true),
            QueryFixtures.helpRow("TITLE", "fields", "Fields", true));

    // The FetchDataResult of a row's source
    private static final String SOURCE_RESULT = """
            {"type": "FetchDataResult", "feedName": "TEST_FEED", "streamTypeName": "Events",
              "sourceLocation": {"metaId": 1001, "partIndex": 0, "recordIndex": 4},
              "itemRange": {"offset": 0, "length": 1}, "totalItemCount": {"count": 1, "exact": true},
              "totalCharacterCount": {"count": 18, "exact": true}, "data": "sample source data",
              "html": false, "errors": [], "availableChildStreamTypes": []}""";

    private static final RestFixtures FIXTURES = fixtures(RestFixtures.builder(), RestReply.json(COMPLETE));

    // AskAiButton: the chat's requests as well
    private static final RestFixtures ASK_AI_FIXTURES = fixtures(AiFixtures.chatRoutes(RestFixtures.builder()),
            RestReply.json(COMPLETE));

    // The description of the query's results table, as the chat's context
    private static final String TABLE_CONTEXT = "Query 'My Query'";

    // QueryHelpPaging: a level with a full page (100 rows), then 5 more
    private static final RestFixtures PAGING_FIXTURES = docAndSearch(RestFixtures.builder()
                    .route(helpItems("fields.").withJsonBodyContaining("{\"pageRequest\": {\"offset\": 0}}"),
                            RestReply.json(fieldPage(0, 100, 105)))
                    .route(helpItems("fields."), RestReply.json(fieldPage(100, 5, 105)))
                    .route(helpItems(""), RestReply.json(QueryFixtures.helpItems(
                            QueryFixtures.helpRow("TITLE", "fields", "Fields", true)))),
            RestReply.json(COMPLETE));

    // SourcePreview: the source of the selected row's event
    private static final RestFixtures SOURCE_FIXTURES = fixtures(RestFixtures.builder()
                    .post("/data/v1/fetch", RestReply.json(SOURCE_RESULT)),
            RestReply.json(COMPLETE));

    // NullCompletion: an incomplete reply with the table, then null (finished)
    private static final RestFixtures NULL_COMPLETION_FIXTURES = fixtures(RestFixtures.builder(),
            RestReply.json(QueryFixtures.response(false, TABLE)),
            RestReply.json("null"));

    // Download: the download's resource
    private static final RestFixtures DOWNLOAD_FIXTURES = fixtures(RestFixtures.builder()
                    .route(RequestMatcher.post("/query/v1/downloadSearchResults/" + RequestMatcher.PATH_WILDCARD),
                            RestReply.json("{\"resourceKey\": {\"key\": \"rk-1\", \"name\": \"results.csv\"}, "
                                           + "\"messageList\": []}")),
            RestReply.json(COMPLETE));

    // ColumnValuesFilterMenu: the column's distinct values
    private static final RestFixtures COLUMN_VALUES_FIXTURES = fixtures(RestFixtures.builder()
                    .route(RequestMatcher.post("/query/v1/columnValues/" + RequestMatcher.PATH_WILDCARD),
                            RestReply.json("{\"values\": [{\"value\": \"alpha\"}, {\"value\": \"beta\"}], "
                                           + "\"pageResponse\": {\"offset\": 0, \"length\": 2, \"total\": 2, "
                                           + "\"exact\": true}}")),
            RestReply.json(COMPLETE));

    private static final RestFixtures HYPERLINK_FIXTURES = fixtures(RestFixtures.builder(),
            RestReply.json(nameTable("[Docs](https://example.com){browser}")));

    // Groups: one group row of a two level grouping
    private static final RestFixtures GROUP_FIXTURES = fixtures(RestFixtures.builder(),
            RestReply.json(QueryFixtures.response(true, QueryFixtures.tableResult("table", """
                            [{"id": "g0", "name": "Region", "group": 0, "visible": true},
                             {"id": "g1", "name": "City", "group": 1, "visible": true},
                             {"id": "v", "name": "Count", "format": {"type": "NUMBER"}, "visible": true}]""",
                    "[{\"values\": [\"North\", \"\", \"\"], \"depth\": 0, \"groupKey\": \"g-north\"}]", 1))));

    // QueryInfoPopup: the UI config asks for the query's justification
    private static final String INFO_POPUP_UI_CONFIG = QueryFixtures.uiConfigWith(
            "\"query\": {\"infoPopup\": {\"enabled\": true, \"title\": \"Justify Query\", "
            + "\"validationRegex\": \"[\\\\s\\\\S]{3,}\"}}");

    // The spies of the events that Stroom's app (not part of this screen) handles
    static final String OPEN_DOC = "openDoc";
    static final String CREATE_ANNOTATION = "createAnnotation";
    static final String EDIT_ANNOTATION = "editAnnotation";
    static final String STEPPING = "beginStepping";
    static final String SHOW_DATA = "showData";
    static final String ANNOTATION_CHANGE = "annotationChange";

    // The text of a query help item
    private static final String HELP_ITEM = ".selectionItemCell-text";

    private static final String FIND_TAGS_PATH = "/annotation/v1/findAnnotationTags";
    private static final String BATCH_CHANGE_PATH = "/annotation/v1/batchChange";

    private static final BiConsumer<ScreenHarness, QueryScreenGinjector> NO_SETUP = (harness, injector) -> {
    };

    // The UI config's analytic rule and report defaults
    private static final String CREATE_UI_CONFIG = QueryFixtures.uiConfigWith("""
            "analyticUiDefaultConfig": {
              "defaultErrorFeed": {"type": "Feed", "uuid": "feed-err", "name": "ERROR_FEED"},
              "defaultDestinationFeed": {"type": "Feed", "uuid": "feed-dst", "name": "DEST_FEED"},
              "defaultNode": "node1"},
            "reportUiDefaultConfig": {
              "defaultErrorFeed": {"type": "Feed", "uuid": "feed-err", "name": "ERROR_FEED"},
              "defaultDestinationFeed": {"type": "Feed", "uuid": "feed-dst", "name": "DEST_FEED"},
              "defaultNode": "node1"}""");

    // Create Rule / Create Report: the query's explorer node, the validation, and the new
    // document, fetched and written (echoed)
    private static RestFixtures.Builder createRoutes(final boolean groupBy) {
        return RestFixtures.builder()
                .post("/explorer/v2/getFromDocRef", RestReply.json(
                        "{\"type\": \"Query\", \"uuid\": \"query-1\", \"name\": \"My Query\"}"))
                .post("/query/v1/validateQuery", RestReply.json(
                        "{\"ok\": true, \"groupBy\": " + groupBy + "}"))
                .get("/analyticRule/v1/new-1", RestReply.json(
                        "{\"type\": \"AnalyticRule\", \"uuid\": \"new-1\", \"name\": \"New Rule\"}"))
                .put("/analyticRule/v1/new-1", request -> RestReply.json(request.getBody()))
                .get("/report/v1/new-1", RestReply.json(
                        "{\"type\": \"Report\", \"uuid\": \"new-1\", \"name\": \"New Report\"}"))
                .put("/report/v1/new-1", request -> RestReply.json(request.getBody()));
    }

    // CreateRuleWithoutDefaults: the server's defaults with nothing configured
    private static final String EMPTY_DEFAULTS_UI_CONFIG = QueryFixtures.uiConfigWith(
            "\"analyticUiDefaultConfig\": {}, \"reportUiDefaultConfig\": {}");

    private static final RestFixtures CREATE_FIXTURES = fixtures(createRoutes(false), RestReply.json(COMPLETE));
    private static final RestFixtures CREATE_GROUP_BY_FIXTURES = fixtures(createRoutes(true),
            RestReply.json(COMPLETE));

    // The annotation endpoints: the annotation statuses, the batch change and an existing
    // annotation to add to
    private static RestFixtures.Builder annotationRoutes(final RestFixtures.Builder builder) {
        return builder
                .post(FIND_TAGS_PATH, RestReply.json("""
                        {"values": [{"id": 1, "uuid": "tag-open", "type": "STATUS", "name": "Open"},
                                    {"id": 2, "uuid": "tag-closed", "type": "STATUS", "name": "Closed"}],
                          "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}"""))
                .post(BATCH_CHANGE_PATH, RestReply.json("1"))
                .post("/annotation/v1/findAnnotations", RestReply.json("""
                        {"values": [{"id": 77, "uuid": "ann-77", "name": "Existing incident", "createUser": "admin",
                          "createTimeMs": 0, "status": {"id": 1, "uuid": "tag-open", "type": "STATUS",
                          "name": "Open"}}],
                          "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""))
                .post("/annotation/v1/change", RestReply.json("true"))
                // Selecting a row also shows its source
                .post("/data/v1/fetch", RestReply.json(SOURCE_RESULT))
                // 'Assign Yourself' shows the user
                .post("/userRef/v1/getUserByUuid", RestReply.json(
                        "{\"uuid\": \"admin-uuid\", \"subjectId\": \"admin\", \"displayName\": \"admin\", "
                        + "\"group\": false, \"enabled\": true}"));
    }

    private static final RestFixtures ANNOTATION_FIXTURES = fixtures(annotationRoutes(RestFixtures.builder()),
            RestReply.json(COMPLETE));

    // AnnotationChangeForcesNewSearch: the query's data source is the Annotations pseudo document
    private static final RestFixtures ANNOTATIONS_DATA_SOURCE_FIXTURES = fixtures(annotationRoutes(
                    RestFixtures.builder()
                            .post("/query/v1/fetchDataSourceFromQueryString", RestReply.json(
                                    "{\"type\": \"Annotations\", \"uuid\": \"Annotations\", "
                                    + "\"name\": \"Annotations\"}"))),
            RestReply.json(COMPLETE));

    private static final RestFixtures STEPPING_FIXTURES = fixtures(RestFixtures.builder(),
            RestReply.json(nameTable("[Step](stepping?id=1001&partNo=1&recordNo=5){stepping}")));

    private static final RestFixtures DATA_LINK_FIXTURES = fixtures(RestFixtures.builder()
                    .post("/data/v1/fetch", RestReply.json(SOURCE_RESULT))
                    .get("/data/v1/1001/parts/0/child-types", RestReply.json("[]"))
                    .get("/meta/v1/1001", RestReply.json(
                            "{\"id\": 1001, \"feedName\": \"TEST_FEED\", \"typeName\": \"Events\", "
                            + "\"status\": \"UNLOCKED\", \"createMs\": 0}")),
            RestReply.json(nameTable(
                    "[View](data?id=1001&partNo=1&recordNo=5&lineFrom=2&colFrom=1&lineTo=3&colTo=9){data}")));

    private static final RestFixtures DATA_TAB_FIXTURES = fixtures(RestFixtures.builder(),
            RestReply.json(nameTable(
                    "[Open](data?id=1001&partNo=1&recordNo=5&viewType=source&displayType=tab){data}")));

    // Visualisation: a vis result as well as the table, and the visualisation document
    private static final String VIS_SETTINGS =
            "{\"json\": \"{}\", \"visualisation\": {\"type\": \"Visualisation\", \"uuid\": \"vis-1\", "
            + "\"name\": \"MyVis\"}}";
    private static final String VIS_DATA =
            "{\\\"values\\\":[[1,2]],\\\"types\\\":[\\\"number\\\",\\\"number\\\"]}";

    private static RestFixtures.Builder visRoutes() {
        return RestFixtures.builder().get("/visualisation/v1/vis-1", RestReply.json(
                "{\"type\": \"Visualisation\", \"uuid\": \"vis-1\", \"name\": \"MyVis\", "
                + "\"functionName\": \"MyVis\"}"));
    }

    private static final RestFixtures VIS_FIXTURES = fixtures(visRoutes(),
            RestReply.json(visResponse(true)));

    // VisualisationDataLatch: data, then a final poll with blank data
    private static final RestFixtures VIS_LATCH_FIXTURES = fixtures(visRoutes(),
            RestReply.json(visResponse(false)),
            RestReply.json(QueryFixtures.response(true, TABLE, QueryFixtures.visResult("vis", VIS_SETTINGS, ""))));

    private QueryEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/QueryEditor", QueryEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The Query tab: toolbar, help, editor and empty results, with the three tabs
                .story("Default", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    for (final String label : new String[]{"Query", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.expect(play.findByRole("button", "Execute Query")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Create Rule")).toBeInTheDocument();
                    play.expect(play.getByText("All time")).toBeInTheDocument();
                    // Stroom shows an empty table, with no message, so the check is that it has no
                    // rows
                    play.expect(play.queryByText("alpha")).toBeNull();
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // Running the query shows the table result
                .story("RunQuery", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    execute(play);
                    play.waitFor(() -> play.expect(header(play, "Name")).toBeInTheDocument());
                    play.expect(header(play, "Count")).toBeInTheDocument();
                    play.expect(play.getByText("alpha")).toBeInTheDocument();
                    play.expect(play.getByText("beta")).toBeInTheDocument();
                    play.expect(play.getByText("20")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // Changing the time range runs the search again with the new range
                .story("TimeRangeRerun", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .toHaveBeenCalledWith(QueryFixtures.SEARCH.toSpyMatcher()));
                    play.click(play.querySelector(".timeRange-selector"));
                    // The popup is a GWT PopupPanel on the page's body
                    play.waitFor(() -> play.expect(screen.querySelector(".timeRange-popup")).toBeInTheDocument());
                    final Play popup = screen.within(screen.querySelector(".timeRange-popup"));
                    play.click(popup.getByText("Today"));
                    play.click(popup.getByRole("button", StroomDom.button("OK")));
                    expectSearchWith(play, "{\"queryContext\": {\"timeRange\": {\"name\": \"Today\"}}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // The query help: the root rows load, expanding one loads its children and
                // selecting a row shows its documentation and enables Insert
                .story("QueryHelp", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    // The help rows are grid cells, not buttons
                    play.waitFor(() -> play.expect(play.getByText("Functions", HELP_ITEM)).toBeInTheDocument());
                    play.expect(play.getByText("Fields", HELP_ITEM)).toBeInTheDocument();
                    play.click(play.getByText("Functions", HELP_ITEM));
                    play.waitFor(() -> play.expect(play.getByText("count", HELP_ITEM)).toBeInTheDocument());
                    play.click(play.getByText("count", HELP_ITEM));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Insert")).not().toHaveClass("disabled"));
                    // The documentation is shown in an iframe, so the check is that it was fetched
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/query/v1/fetchDetail")
                                    .withJsonBodyContaining("{\"title\": \"count\"}").toSpyMatcher());
                    DocumentEditors.expectNoProblems(play);
                })
                // The query help pages each level: a full page (100 rows) offers the next page
                .story("QueryHelpPaging", context -> render(context, PAGING_FIXTURES))
                .withPlay(play -> {
                    play.click(play.findByText("Fields", HELP_ITEM));
                    play.waitFor(() -> play.expect(play.getByText("field_0", HELP_ITEM)).toBeInTheDocument());
                    play.expect(play.getByText("field_99", HELP_ITEM)).toBeInTheDocument();
                    // Stroom pages the level with its pager ('1 to 100 of 105'), replacing the page; there
                    // is no 'Show more' to append rows
                    play.expect(play.queryByText(TextMatch.startingWith("Show more"))).toBeNull();
                    play.expect(play.querySelectorAll(".pager-paging").nth(0).textContent())
                            .toSatisfy("shows '1 to 100 of 105'", text ->
                                    String.valueOf(text).replace('\u00a0', ' ').contains("1 to 100 of 105"));
                    play.click(play.getAllByRole("button", "Forward").nth(0));
                    play.waitFor(() -> play.expect(play.getByText("field_104", HELP_ITEM)).toBeInTheDocument());
                    play.expect(play.queryByText("field_0", HELP_ITEM)).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // Selecting a row with a stream and event id shows its source at record
                // eventId - 1
                .story("SourcePreview", context -> render(context, SOURCE_FIXTURES))
                .withPlay(play -> {
                    execute(play);
                    play.click(play.findByText("alpha"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/data/v1/fetch").withJsonBodyContaining(
                                    "{\"sourceLocation\": {\"metaId\": 1001, \"recordIndex\": 4}}").toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // A null search reply ends the search without an error
                .story("NullCompletion", context -> render(context, NULL_COMPLETION_FIXTURES))
                .withPlay(play -> {
                    execute(play);
                    play.findByText("alpha");
                    play.waitFor(() -> play.expect(play.getByRole("button", "Execute Query")).toBeInTheDocument());
                    // With no errors, GWT's 'Show Errors' button holds its place in the toolbar but
                    // is hidden: it can't be seen, reached or read (it was once only transparent)
                    play.expect(play.queryByRole("button", "Show Errors")).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // Download: the Download Options dialog asks for the current search's results
                .story("Download", context -> render(context, DOWNLOAD_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.findByText("alpha");
                    play.click(play.getByRole("button", "Download"));
                    // Stroom's dialogs have no role="dialog"
                    final Play dialog = dialog(screen, "Download Options");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/query/v1/downloadSearchResults/" + RequestMatcher.PATH_WILDCARD)
                                    .withJsonBodyContaining("{\"fileType\": \"EXCEL\", \"searchRequest\": "
                                                            + "{\"queryKey\": {\"uuid\": \"qk-1\"}}}")
                                    .toSpyMatcher()));
                    // The fixture replies with a resource, which Stroom downloads
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.DOWNLOAD_SPY)).toHaveBeenCalled());
                    DocumentEditors.expectNoProblems(play);
                })
                // The column menu's Hide hides the column, searching again with the column marked
                // as not visible in the table's preferences
                .story("ColumnMenu", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    executeAndOpenColumnMenu(play, "Count");
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    play.click(play.screen().findByText("Hide", StroomDom.MENU_ITEM_TEXT));
                    // Stroom hides the column at once, without searching again; the preference is
                    // kept on the document, which is now dirty, and is sent with the next search
                    play.waitFor(() -> play.expect(play.queryByText("Count", ".column-top .column-label")).toBeNull());
                    play.expect(play.getByRole("button", "Save")).not().toHaveClass("disabled");
                    play.click(play.getByRole("button", "Execute Query"));
                    expectSearchWith(play, "{\"queryTablePreferences\": {\"columns\": "
                                           + "[{}, {\"name\": \"Count\", \"visible\": false}, {}, {}]}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // The column menu's Filter sets the column's filter
                .story("ColumnFilterMenu", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    executeAndOpenColumnMenu(play, "Count");
                    play.click(screen.findByText("Filter", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = dialog(screen, "Filter 'Count'");
                    // The filter is an Ace editor
                    play.waitFor(() -> play.expect(dialog.querySelector(".ace_text-input")).toBeInTheDocument());
                    // Clicking the editor's content focuses its text input
                    play.click(dialog.querySelector(".ace_content"));
                    play.keyboard("foo");
                    play.waitFor(() -> play.expect(dialog.querySelector(".ace_content").textContent())
                            .toMatch(TextMatch.containing("foo")));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectSearchWith(play, "{\"queryTablePreferences\": {\"columns\": "
                                           + "[{}, {\"name\": \"Count\", \"columnFilter\": {\"filter\": \"foo\"}}, "
                                           + "{}, {}]}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // The query's column filter has no Include/Exclude tab (unlike a dashboard's)
                .story("QuerySideFilterHasNoIncludeExcludeTab", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    executeAndOpenColumnMenu(play, "Count");
                    play.click(screen.findByText("Filter", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = dialog(screen, "Filter 'Count'");
                    play.expect(dialog.queryByText("Include Exclude")).toBeNull();
                    play.waitFor(() -> play.expect(dialog.querySelector(".ace_text-input")).toBeInTheDocument());
                    DocumentEditors.expectNoProblems(play);
                })
                // The column's values filter: its distinct values, of which a whitelist is kept
                .story("ColumnValuesFilterMenu", context -> render(context, COLUMN_VALUES_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.findByText("alpha");
                    // The values filter is opened by the filter icon of the column's header
                    clickHeader(play, play.within(header(play, "Count").closest("th"))
                            .querySelector(".column-valueFilterIcon"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/query/v1/columnValues/" + RequestMatcher.PATH_WILDCARD)
                                    .toSpyMatcher()));
                    play.click(screen.findByText("Select None"));
                    // The popup's 'alpha' is after the table's on the page.
                    // The popup has no OK; a choice applies at once
                    play.waitFor(() -> play.expect(screen.getAllByText("alpha")).toHaveLength(2));
                    play.click(screen.getAllByText("alpha").nth(1));
                    expectSearchWith(play, "{\"queryTablePreferences\": {\"columns\": [{}, {\"name\": \"Count\", "
                                           + "\"columnValueSelection\": {\"invert\": false, "
                                           + "\"values\": [\"alpha\"]}}, {}, {}]}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // The column menu's Format sets the column's format
                .story("FormatMenu", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    executeAndOpenColumnMenu(play, "Name");
                    play.click(screen.findByText("Format", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = dialog(screen, "Format 'Name'");
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Number", ".SelectionPopup *"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // The play formats 'Name' ('Count' is a Number already, so choosing Number would
                    // change nothing)
                    expectSearchWith(play, "{\"queryTablePreferences\": {\"columns\": "
                                           + "[{\"name\": \"Name\", \"format\": {\"type\": \"NUMBER\"}}, "
                                           + "{}, {}, {}]}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // Conditional formatting: the rules list, a new rule, then OK
                .story("ConditionalFormattingMenu", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    executeAndOpenColumnMenu(play, "Count");
                    play.click(screen.findByText("Conditional Formatting", StroomDom.MENU_ITEM_TEXT));
                    // The rules dialog is captioned 'Settings'
                    final Play list = dialog(screen, "Settings");
                    play.click(list.getByRole("button", "Add"));
                    final Play rule = dialog(screen, "Add New Rule");
                    play.click(rule.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Add New Rule", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // The rules are a grid: Enabled, Expression, Style and Hide Row
                    for (final String heading : new String[]{"Enabled", "Expression", "Style", "Hide Row"}) {
                        play.expect(list.getByText(heading)).toBeInTheDocument();
                    }
                    play.click(list.getByRole("button", StroomDom.button("OK")));
                    expectSearchWith(play, "{\"queryTablePreferences\": {\"conditionalFormattingRules\": [{}]}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // A '[text](href){type}' value is shown as a link
                .story("Hyperlinks", context -> render(context, HYPERLINK_FIXTURES))
                .withPlay(play -> {
                    execute(play);
                    final Query link = play.findByText("Docs");
                    play.expect(play.queryByText(TextMatch.containing("[Docs]"))).toBeNull();
                    play.expect(link.property("tagName")).toBe("U");
                    // Stroom URL encodes the link in the attribute
                    play.expect(link.attribute("link")).toMatch("https%3A%2F%2Fexample.com");
                    DocumentEditors.expectNoProblems(play);
                })
                // A group row has an expander; expanding it fetches again with the open group
                .story("Groups", context -> render(context, GROUP_FIXTURES))
                .withPlay(play -> {
                    execute(play);
                    play.findByText("North");
                    // The ExpanderCell icon has no title
                    play.click(play.querySelector(".expanderCell .expanderIcon"));
                    expectSearchWith(play, "{\"groupSelection\": {\"openGroups\": [\"g-north\"]}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // The search request carries the user's date and time settings
                .story("SearchRequestCarriesDateTimeSettings", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    execute(play);
                    // The user's preferences (Stroom's defaults here) are checked in the request
                    expectSearchWith(play, "{\"queryContext\": {\"dateTimeSettings\": "
                                           + "{\"dateTimePattern\": \"yyyy-MM-dd'T'HH:mm:ss.SSSXX\", "
                                           + "\"timeZone\": {\"use\": \"UTC\"}}}}");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            QueryFixtures.SEARCH.withBody("a localZoneId and referenceTime", body ->
                                            body.contains("\"localZoneId\":\"") && body.contains("\"referenceTime\":"))
                                    .toSpyMatcher());
                    DocumentEditors.expectNoProblems(play);
                })
                // With the query info popup enabled, Execute first asks for a justification
                .story("QueryInfoPopup", context -> render(context, FIXTURES, INFO_POPUP_UI_CONFIG, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", "Execute Query"));
                    final Play dialog = dialog(screen, "Justify Query");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            QueryFixtures.SEARCH.toSpyMatcher());
                    // The text area has no id
                    play.type(dialog.querySelector("textarea"), "investigating alert 42");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectSearchWith(play, "{\"queryContext\": {\"queryInfo\": \"investigating alert 42\"}}");
                    DocumentEditors.expectNoProblems(play);
                })
                // The results table's 'Ask Stroom AI' button asks the AI about the table
                .story("AskAiButton", context -> render(context, ASK_AI_FIXTURES, null, false,
                        QueryEditorStories::askAi))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.findByText("alpha");
                    play.click(play.getByRole("button", "Ask Stroom AI"));
                    // The chat is Stroom's 'Ask Stroom AI' dialog, and its 'How can I help?'
                    // greeting is hidden once the table is attached as the chat's context, so the
                    // play checks the message box's placeholder, which is the same text
                    play.expect(screen.findByText("Ask Stroom AI", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.waitFor(() -> play.expect(screen.getByPlaceholderText("How can I help?")).toBeVisible());
                    // The chat is titled with the table's context, which is sent to the server
                    play.waitFor(() -> play.expect(screen.getByText(TABLE_CONTEXT)).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/ai/v1/askStroomAi")
                                    .withJsonBodyContaining("{\"context\": {\"type\": \"queryTable\", "
                                                            + "\"description\": \"" + TABLE_CONTEXT + "\"}}")
                                    .toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // Create Rule: validates the query, chooses STREAMING (not grouped), asks for a new
                // rule, and the new rule is written with the query and the default feeds
                .story("CreateRule", context -> render(context, CREATE_FIXTURES, CREATE_UI_CONFIG, false,
                        QueryEditorStories::createProbe))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Create Rule"));
                    expectCreateRequest(play, "AnalyticRule", "Create New Analytic Rule");
                    play.expect(play.getByTestId("pending-name")).toHaveTextContent("My Query");
                    play.click(play.getByRole("button", "run-seed"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/analyticRule/v1/new-1").withJsonBodyContaining("""
                                    {"analyticProcessType": "STREAMING",
                                     "query": "from index\\nselect name, count\\n",
                                     "errorFeed": {"name": "ERROR_FEED"},
                                     "notifications": [{"maxNotifications": 100, "destinationType": "STREAM"}]}
                                    """).toSpyMatcher()));
                    // Stroom then opens the new rule
                    play.waitFor(() -> play.expect(play.spy(OPEN_DOC)).toHaveBeenCalledWith("new-1"));
                    DocumentEditors.expectNoProblems(play);
                })
                // Create Rule with no UI defaults: the rule is still created, with no error feed
                .story("CreateRuleWithoutDefaults", context -> render(context, CREATE_FIXTURES,
                        EMPTY_DEFAULTS_UI_CONFIG, false,
                        QueryEditorStories::createProbe))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Create Rule"));
                    expectCreateRequest(play, "AnalyticRule", "Create New Analytic Rule");
                    play.click(play.getByRole("button", "run-seed"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/analyticRule/v1/new-1")
                                    .withJsonBodyContaining("{\"query\": \"from index\\nselect name, count\\n\"}")
                                    .withBody("no error feed", body -> !body.contains("\"errorFeed\":{"))
                                    .toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // Create Report: always a scheduled query
                .story("CreateReport", context -> render(context, CREATE_FIXTURES, CREATE_UI_CONFIG, false,
                        QueryEditorStories::createProbe))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Create Report"));
                    expectCreateRequest(play, "Report", "Create New Report");
                    play.click(play.getByRole("button", "run-seed"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/report/v1/new-1").withJsonBodyContaining("""
                                    {"analyticProcessType": "SCHEDULED_QUERY",
                                     "query": "from index\\nselect name, count\\n",
                                     "errorFeed": {"name": "ERROR_FEED"},
                                     "notifications": [{}]}
                                    """).toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // A grouped query makes Create Rule choose SCHEDULED_QUERY
                .story("CreateRuleGroupBy", context -> render(context, CREATE_GROUP_BY_FIXTURES, CREATE_UI_CONFIG,
                        false, QueryEditorStories::createProbe))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Create Rule"));
                    expectCreateRequest(play, "AnalyticRule", "Create New Analytic Rule");
                    play.click(play.getByRole("button", "run-seed"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/analyticRule/v1/new-1")
                                    .withJsonBodyContaining("{\"analyticProcessType\": \"SCHEDULED_QUERY\"}")
                                    .toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // Annotations: Create Annotation links the selected row's event; Edit opens a
                // row's annotation
                .story("Annotations", context -> render(context, ANNOTATION_FIXTURES, null, false,
                        QueryEditorStories::annotationSpies))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.click(play.findByText("alpha"));
                    // The actions are in the 'Annotate' button's menu
                    play.click(play.getByRole("button", "Annotate"));
                    play.click(screen.findByText("Create Annotation", StroomDom.MENU_ITEM_TEXT));
                    // Stroom fires CreateAnnotationEvent, which Stroom's annotation plugin (not part
                    // of this screen) handles by creating and opening it
                    play.waitFor(() -> play.expect(play.spy(CREATE_ANNOTATION)).toHaveBeenCalledWith("[1001:5]"));
                    play.click(play.getByText("beta"));
                    play.click(play.getByRole("button", "Annotate"));
                    play.click(screen.findByText("Edit Annotation", StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(play.spy(EDIT_ANNOTATION)).toHaveBeenCalledWith("55"));
                    DocumentEditors.expectNoProblems(play);
                })
                // The statuses are only fetched when the status chooser opens
                .story("AnnotationStatusesLoadOnMenuOpen", context -> render(context, ANNOTATION_FIXTURES, null,
                        false, QueryEditorStories::annotationSpies))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.click(play.findByText("beta"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_TAGS_PATH).toSpyMatcher());
                    play.click(play.getByRole("button", "Annotate"));
                    play.click(screen.findByText("Change Status", StroomDom.MENU_ITEM_TEXT));
                    // The statuses are a chooser in the 'Change Status' dialog
                    final Play dialog = dialog(screen, "Change Status");
                    play.click(dialog.querySelector(".annotationSettingHeading"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_TAGS_PATH).toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.getByText("Open")).toBeInTheDocument());
                    DocumentEditors.expectNoProblems(play);
                })
                // Assigning the selected row's annotation to the user changes it in a batch
                .story("AnnotationBatch", context -> render(context, ANNOTATION_FIXTURES, null, false,
                        QueryEditorStories::annotationSpies))
                .withPlay(play -> {
                    assignToMe(play);
                    DocumentEditors.expectNoProblems(play);
                })
                // Change Assigned To with no user chosen clears the assignment
                .story("ChangeAssignedTo", context -> render(context, ANNOTATION_FIXTURES, null, false,
                        QueryEditorStories::annotationSpies))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.click(play.findByText("beta"));
                    play.click(play.getByRole("button", "Annotate"));
                    play.click(screen.findByText("Change Assigned To", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = dialog(screen, "Change Assigned To");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(BATCH_CHANGE_PATH).withJsonBodyContaining(
                                    "{\"annotationIdList\": [55], \"change\": {\"type\": \"assignedTo\", "
                                    + "\"userRef\": null}}").toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // Add To Annotation: choose an existing annotation, link the row's event to it and
                // open it
                .story("AddToAnnotation", context -> render(context, ANNOTATION_FIXTURES, null, false, true,
                        QueryEditorStories::findAnnotation))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.click(play.findByText("alpha"));
                    play.click(play.getByRole("button", "Annotate"));
                    play.click(screen.findByText("Add To Annotation", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = dialog(screen, "Choose Annotation");
                    // Stroom doesn't select the first annotation (the list only selects it when the
                    // filter changes), so OK with none selected warns and keeps the chooser open;
                    // the play then selects it
                    play.expect(dialog.findByText("Existing incident").closest("tr"))
                            .not().toHaveClass("cellTableSelectedRow");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    final Play warning = screen.within(screen.findByText(NOTHING_SELECTED).closest(StroomDom.DIALOG));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith("WARN: " + NOTHING_SELECTED);
                    play.click(warning.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText(NOTHING_SELECTED)).toBeNull());
                    play.expect(screen.getByText("Choose Annotation", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/annotation/v1/change").toSpyMatcher());
                    play.click(dialog.getByText("Existing incident"));
                    play.waitFor(() -> play.expect(dialog.getByText("Existing incident").closest("tr"))
                            .toHaveClass("cellTableSelectedRow"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/annotation/v1/change").withJsonBodyContaining(
                                    "{\"annotationRef\": {\"uuid\": \"ann-77\"}, \"change\": {\"type\": "
                                    + "\"linkEvents\", \"events\": [{\"streamId\": 1001, \"eventId\": 5}]}}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(EDIT_ANNOTATION)).toHaveBeenCalledWith("77"));
                    // The warning is the only alert
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A STEPPING link begins stepping at the 0 based part and record
                .story("SteppingHyperlink", context -> render(context, STEPPING_FIXTURES, null, false,
                        QueryEditorStories::hyperlinks))
                .withPlay(play -> {
                    execute(play);
                    play.click(play.findByText("Step"));
                    // Stroom fires BeginPipelineSteppingEvent, which the stepping screen (not part
                    // of this screen) handles
                    play.waitFor(() -> play.expect(play.spy(STEPPING)).toHaveBeenCalledWith("1001:0:4"));
                    DocumentEditors.expectNoProblems(play);
                })
                // A DATA link opens a dialog of the record, fetched with the link's range
                .story("DataHyperlink", context -> render(context, DATA_LINK_FIXTURES, null, false,
                        QueryEditorStories::dataDialog))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    execute(play);
                    play.click(play.findByText("View"));
                    play.expect(screen.findByText("Stream 1001:1:5", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/data/v1/fetch").withJsonBodyContaining(
                                    "{\"sourceLocation\": {\"metaId\": 1001, \"recordIndex\": 4, \"dataRange\": "
                                    + "{\"locationFrom\": {\"lineNo\": 2}, \"locationTo\": {\"colNo\": 9}}}}")
                                    .toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // A DATA link with displayType=tab opens the stream in a tab
                .story("DataStroomTab", context -> render(context, DATA_TAB_FIXTURES, null, false,
                        QueryEditorStories::hyperlinks))
                .withPlay(play -> {
                    execute(play);
                    play.click(play.findByText("Open"));
                    // Stroom fires ShowDataEvent, which Stroom's data tab plugins (not part of this
                    // screen) handle
                    play.waitFor(() -> play.expect(play.spy(SHOW_DATA))
                            .toHaveBeenCalledWith("STROOM_TAB SOURCE 1001:4"));
                    DocumentEditors.expectNoProblems(play);
                })
                // When the query's data source is Annotations, changing an annotation runs a new
                // search (with no query key), rather than refreshing the old one
                .story("AnnotationChangeForcesNewSearch", context -> render(context, ANNOTATIONS_DATA_SOURCE_FIXTURES,
                        null, false, QueryEditorStories::annotationSpies))
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    // Let the query's data source (Annotations) be fetched
                    play.waitFor(() -> play.expect(requests).toHaveBeenCalledWith(RequestMatcher.post(
                            "/query/v1/fetchDataSourceFromQueryString").toSpyMatcher()));
                    assignToMe(play);
                    play.waitFor(() -> play.expect(play.spy(ANNOTATION_CHANGE)).toHaveBeenCalled());
                    play.waitFor(() -> play.expect("new searches", () -> newSearches(requests)).toBe(2));
                    DocumentEditors.expectNoProblems(play);
                })
                // A vis result shows the Visualisation tab, which loads the visualisation
                .story("Visualisation", context -> render(context, VIS_FIXTURES))
                .withPlay(play -> {
                    // The result tabs have no role="tab", and the hidden Visualisation tab is in the
                    // page, not shown
                    play.waitFor(() -> play.expect(DocumentEditors.tab(play, "Table")).toBeInTheDocument());
                    play.expect(DocumentEditors.tab(play, "Visualisation")).not().toBeVisible();
                    execute(play);
                    play.waitFor(8000, () -> play.expect(DocumentEditors.tab(play, "Visualisation")).toBeVisible());
                    play.waitFor(8000, () -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get("/visualisation/v1/vis-1").toSpyMatcher()));
                    // The tab is selected, and the visualisation's frame is on the page's body
                    play.expect(DocumentEditors.tab(play, "Visualisation").closest(".linkTab"))
                            .toHaveClass("linkTab-selected");
                    play.expect(play.screen().querySelector("iframe")).toBeInTheDocument();
                    play.click(DocumentEditors.tab(play, "Table"));
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeVisible());
                    DocumentEditors.expectNoProblems(play);
                })
                // The Visualisation tab stays once it has had data, even if a later poll has none
                .story("VisualisationDataLatch", context -> render(context, VIS_LATCH_FIXTURES))
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    execute(play);
                    play.waitFor(8000, () -> play.expect(DocumentEditors.tab(play, "Visualisation")).toBeVisible());
                    play.waitFor(() -> play.expect("searches", () -> searches(requests)).toBe(2));
                    play.expect(DocumentEditors.tab(play, "Visualisation")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only: Save can never be enabled, but the query can still be run
                .story("ReadOnly", context -> render(context, FIXTURES, null, true))
                .withPlay(play -> {
                    play.expect(play.findByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    play.expect(play.getByRole("button", "Execute Query")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                });
    }

    private static RestFixtures.Builder helpRoutes(final RestFixtures.Builder builder) {
        return builder
                .route(helpItems("functions."), RestReply.json(QueryFixtures.helpItems(
                        QueryFixtures.helpRow("FUNCTION", "fn-count", "count", false))))
                .route(helpItems("fields."), RestReply.json(QueryFixtures.helpItems(
                        QueryFixtures.helpRow("FIELD", "fld-name", "name", false))))
                .route(helpItems(""), RestReply.json(HELP_ROOT))
                // The help detail: the row's title as its documentation and insert text
                .route(RequestMatcher.post("/query/v1/fetchDetail")
                                .withJsonBodyContaining("{\"title\": \"count\"}"),
                        RestReply.json("{\"documentation\": \"# count\\n\\nHelp for **count**.\", "
                                       + "\"insertText\": \"count\", \"insertType\": \"PLAIN_TEXT\"}"));
    }

    private static RequestMatcher helpItems(final String parentPath) {
        return RequestMatcher.post(QueryFixtures.HELP_ITEMS_PATH)
                .withJsonBodyContaining("{\"parentPath\": \"" + parentPath + "\"}");
    }

    // The fixtures of a story: its own routes first, then the search's replies (a sequence),
    // the help, the document and the editor's defaults
    private static RestFixtures fixtures(final RestFixtures.Builder storyRoutes,
                                         final RestReply search,
                                         final RestReply... moreSearch) {
        return docAndSearch(helpRoutes(storyRoutes), search, moreSearch);
    }

    // The fixtures of a story with its own help routes
    private static RestFixtures docAndSearch(final RestFixtures.Builder storyRoutes,
                                             final RestReply search,
                                             final RestReply... moreSearch) {
        return QueryFixtures.editorRoutes(DocumentEditors.ownerPermissions(storyRoutes
                        .get("/query/v1/query-1", RestReply.json(DOC))
                        .route(QueryFixtures.SEARCH, search, moreSearch)))
                .build();
    }

    // A complete search of a table of one 'Name' column holding one value, e.g. a hyperlink
    private static String nameTable(final String value) {
        return QueryFixtures.response(true, QueryFixtures.tableResult("table",
                "[{\"id\": \"f-name\", \"name\": \"Name\", \"visible\": true}]",
                "[{\"values\": [\"" + value + "\"], \"depth\": 0}]", 1));
    }

    // A page of help rows field_<from> to field_<from + count - 1> of `total`
    private static String fieldPage(final int from, final int count, final int total) {
        final String[] rows = new String[count];
        for (int i = 0; i < count; i++) {
            rows[i] = QueryFixtures.helpRow("FIELD", "fld-" + (from + i), "field_" + (from + i), false);
        }
        return "{\"values\": [" + String.join(", ", rows) + "], \"pageResponse\": {\"offset\": " + from
               + ", \"length\": " + count + ", \"total\": " + total + ", \"exact\": true}}";
    }

    // The header of a column of the results table
    private static Query header(final Play play, final String name) {
        return play.getByText(name, ".column-top .column-label");
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    private static void execute(final Play play) {
        play.click(play.findByRole("button", "Execute Query"));
    }

    // Runs the query and opens the menu of a column.
    // Stroom opens a column's menu when its header is clicked (MyDataGrid's
    // HeadingListener.onShowMenu), not on a context menu
    private static void executeAndOpenColumnMenu(final Play play, final String column) {
        execute(play);
        play.findByText("alpha");
        clickHeader(play, header(play, column));
    }

    /// Clicks a part of a results table's column header. MyDataGrid only handles a header's mouse
    /// buttons once the mouse has moved over it (it attaches a native preview handler after the
    /// move, and drops it when the mouse leaves), so the play moves the mouse over the header,
    /// waits, then presses and releases the button there without moving again (user-event's
    /// click would move the mouse in from the page's body).
    ///
    /// @param play   The play.
    /// @param target The part of the header to click, e.g. its label or value filter icon.
    static void clickHeader(final Play play, final Query target) {
        play.fireEvent().mouseMove(target, EventInit.create().atCentreOf(target));
        play.sleep(200);
        play.fireEvent().mouseDown(target, EventInit.create().atCentreOf(target).buttons(1));
        play.fireEvent().mouseUp(target, EventInit.create().atCentreOf(target));
        play.fireEvent().click(target, EventInit.create().atCentreOf(target));
    }

    // Checks the request for a new document that ShowCreateDocumentDialogEvent makes
    private static void expectCreateRequest(final Play play, final String type, final String title) {
        play.waitFor(() -> play.expect(play.getByTestId("pending-type")).toHaveTextContent(type));
        play.expect(play.getByTestId("pending-title")).toHaveTextContent(title);
    }

    // Assigns the selected row's annotation to the current user.
    // The 'Change Assigned To' dialog has 'Assign Yourself'
    private static void assignToMe(final Play play) {
        final Play screen = play.screen();
        execute(play);
        play.click(play.findByText("beta"));
        play.click(play.getByRole("button", "Annotate"));
        play.click(screen.findByText("Change Assigned To", StroomDom.MENU_ITEM_TEXT));
        final Play dialog = dialog(screen, "Change Assigned To");
        play.click(dialog.getByText("Assign Yourself"));
        play.click(dialog.getByRole("button", StroomDom.button("OK")));
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post(BATCH_CHANGE_PATH).withJsonBodyContaining(
                        "{\"annotationIdList\": [55], \"change\": {\"type\": \"assignedTo\", "
                        + "\"userRef\": {\"uuid\": \"admin-uuid\"}}}").toSpyMatcher()));
    }

    private static void expectSearchWith(final Play play, final String json) {
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                QueryFixtures.SEARCH.withJsonBodyContaining(json).toSpyMatcher()));
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        return render(context, fixtures, null, false, NO_SETUP);
    }

    /// Renders the Query editor of the stories' query ('My Query'), for other stories that show a
    /// part of it, e.g. `App/Dashboard/QueryResultVis`.
    ///
    /// @param context  The story's context.
    /// @param fixtures The story's fixtures, e.g. [#visFixtures].
    /// @return The story's widget.
    public static Widget renderEditor(final StoryContext context, final RestFixtures fixtures) {
        return render(context, fixtures);
    }

    /// The fixtures of a search whose replies have the table and a visualisation result
    /// (the visualisation document `vis-1`), for [#renderEditor].
    ///
    /// @param search     The first reply.
    /// @param moreSearch Any later replies.
    /// @return The fixtures.
    public static RestFixtures visFixtures(final RestReply search, final RestReply... moreSearch) {
        return fixtures(visRoutes(), search, moreSearch);
    }

    /// The fixtures of a complete search with the given results, for [#renderEditor].
    ///
    /// @param results The JSON of each component's result, e.g. `QueryFixtures.tableResult(...)`.
    /// @return The fixtures.
    public static RestFixtures searchFixtures(final String... results) {
        return fixtures(RestFixtures.builder(), RestReply.json(QueryFixtures.response(true, results)));
    }

    /// A search reply with the table and a visualisation result with data.
    ///
    /// @param complete Whether the search is complete.
    /// @return The reply's JSON.
    public static String visResponse(final boolean complete) {
        return QueryFixtures.response(complete, TABLE, QueryFixtures.visResult("vis", VIS_SETTINGS, VIS_DATA));
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final String uiConfig,
                                 final boolean readOnly) {
        return render(context, fixtures, uiConfig, readOnly, NO_SETUP);
    }

    // Renders the editor; `setup` adds what Stroom's app would have around it (event handlers)
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final String uiConfig,
                                 final boolean readOnly,
                                 final BiConsumer<ScreenHarness, QueryScreenGinjector> setup) {
        return render(context, fixtures, uiConfig, readOnly, false, setup);
    }

    // As above; with `realAlerts`, alerts are shown as Stroom shows them (e.g. for a play that
    // dismisses one)
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final String uiConfig,
                                 final boolean readOnly,
                                 final boolean realAlerts,
                                 final BiConsumer<ScreenHarness, QueryScreenGinjector> setup) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures).injector(injector);
        if (uiConfig != null) {
            builder.uiConfig(uiConfig);
        }
        if (realAlerts) {
            builder.realAlerts();
        }
        final ScreenHarness harness = builder.build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        setup.accept(harness, injector);
        final QueryResource resource = GWT.create(QueryResource.class);
        // Opened once Stroom has started, as QueryPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getQueryDocPresenter(), DOC_REF,
                resource, res -> res.fetch(DOC_REF.getUuid())));
        return harness.asWidget();
    }

    // Stands in for the explorer's 'create document' dialog: shows what
    // ShowCreateDocumentDialogEvent asks for, with a 'run-seed' button that creates the document
    // 'new-1' as the dialog would
    private static void createProbe(final ScreenHarness harness, final QueryScreenGinjector injector) {
        harness.fn(OPEN_DOC);
        harness.getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(OPEN_DOC, event.getDocRef().getUuid()));
        final FlowPanel probe = new FlowPanel();
        probe.getElement().getStyle().setPosition(Position.ABSOLUTE);
        probe.getElement().getStyle().setBottom(0, Unit.PX);
        probe.getElement().getStyle().setRight(0, Unit.PX);
        probe.getElement().getStyle().setZIndex(10);
        final InlineLabel type = probeLabel(probe, "pending-type");
        final InlineLabel title = probeLabel(probe, "pending-title");
        final InlineLabel name = probeLabel(probe, "pending-name");
        harness.getEventBus().addHandler(ShowCreateDocumentDialogEvent.getType(), event -> {
            type.setText(event.getDocType());
            title.setText(event.getDialogCaption());
            name.setText(event.getInitialDocName());
            final Button seed = new Button("run-seed", (ClickHandler) click -> event.getNewDocConsumer().accept(
                    ExplorerNode.builder()
                            .docRef(new DocRef(event.getDocType(), "new-1", "New"))
                            .build()));
            probe.add(seed);
        });
        harness.add(probe);
    }

    private static InlineLabel probeLabel(final FlowPanel probe, final String testId) {
        final InlineLabel label = new InlineLabel();
        label.getElement().setAttribute("data-testid", testId);
        probe.add(label);
        return label;
    }

    // Spies on the annotation events that Stroom's annotation plugins handle
    private static void annotationSpies(final ScreenHarness harness, final QueryScreenGinjector injector) {
        harness.fn(CREATE_ANNOTATION);
        harness.fn(EDIT_ANNOTATION);
        harness.getEventBus().addHandler(CreateAnnotationEvent.getType(), event ->
                harness.spy(CREATE_ANNOTATION, String.valueOf(event.getLinkedEvents())));
        harness.getEventBus().addHandler(EditAnnotationEvent.getType(), event ->
                harness.spy(EDIT_ANNOTATION, String.valueOf(event.getAnnotationId())));
        harness.fn(ANNOTATION_CHANGE);
        harness.getEventBus().addHandler(AnnotationChangeEvent.getType(), event ->
                harness.spy(ANNOTATION_CHANGE, String.valueOf(event.getAnnotationRef())));
    }

    // As annotationSpies, with the 'Choose Annotation' dialog shown by ShowFindAnnotationEvent
    private static void findAnnotation(final ScreenHarness harness, final QueryScreenGinjector injector) {
        annotationSpies(harness, injector);
        final FindAnnotationPresenter presenter = injector.getFindAnnotationPresenter();
        harness.getEventBus().addHandler(ShowFindAnnotationEvent.getType(), presenter);
    }

    // Stroom's handler of a table's links, with spies on the events it fires
    private static void hyperlinks(final ScreenHarness harness, final QueryScreenGinjector injector) {
        harness.fn(STEPPING);
        harness.fn(SHOW_DATA);
        injector.getHyperlinkEventHandler();
        harness.getEventBus().addHandler(BeginPipelineSteppingEvent.getType(), event ->
                harness.spy(STEPPING, event.getStepLocation().getMetaId() + ":"
                                      + event.getStepLocation().getPartIndex() + ":"
                                      + event.getStepLocation().getRecordIndex()));
        harness.getEventBus().addHandler(ShowDataEvent.getType(), event ->
                harness.spy(SHOW_DATA, event.getDisplayMode() + " " + event.getDataViewType() + " "
                                       + event.getSourceLocation().getMetaId() + ":"
                                       + event.getSourceLocation().getRecordIndex()));
    }

    // As hyperlinks, with Stroom's handler of ShowDataEvent, which shows a data link's dialog
    private static void dataDialog(final ScreenHarness harness, final QueryScreenGinjector injector) {
        hyperlinks(harness, injector);
        injector.getDataDisplaySupport();
    }

    // As the app does: Stroom's chat handles the table's AskStroomAiEvent
    private static void askAi(final ScreenHarness harness, final QueryScreenGinjector injector) {
        AskStroomAiChat.register(harness, injector::getAskStroomAiPresenter);
    }

    // The number of searches made
    private static int searches(final Spy requests) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            if (String.valueOf(call.get(0)).startsWith("POST /query/v1/search/")) {
                count++;
            }
        }
        return count;
    }

    // The number of searches made without a query key (new searches)
    private static int newSearches(final Spy requests) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            final String request = String.valueOf(call.get(0));
            if (request.startsWith("POST /query/v1/search/") && request.contains("\"queryKey\":null")) {
                count++;
            }
        }
        return count;
    }
}
