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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleResource;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.result.component.ResultTable;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Editors/AnalyticRuleEditor` stories, showing Stroom's real `AnalyticRuleEditor` (an
/// analytic rule's editor tab: Query, Settings, Notifications, Execution, Shards or Duplicate
/// Management, Documentation and Permissions) with fake REST replies.
///
/// As `AnalyticsPlugin` does, the story fetches the document (`GET /analyticRule/v1/{uuid}`),
/// checks the user may edit it and reads it into the editor. The query pane's requests are
/// answered by [QueryFixtures] (as for `App/Editors/QueryEditor`), and the other tabs' by Stroom's
/// analytic rule, execution schedule, duplicate check and document permission endpoints.
public final class AnalyticRuleEditorStories {

    /// The UI config's analytic rules' defaults.
    static final String UI_CONFIG = QueryFixtures.uiConfigWith("""
            "analyticUiDefaultConfig": {"defaultNode": "node1",
              "defaultSubjectTemplate": "Detection: {{ ruleName }}",
              "defaultBodyTemplate": "A detection occurred."},
            "reportUiDefaultConfig": {}""");

    // NewScheduleSeedsTheDefaultNode: the default node is node9
    private static final String NODE9_UI_CONFIG = QueryFixtures.uiConfigWith(
            "\"analyticUiDefaultConfig\": {\"defaultNode\": \"node9\"}, \"reportUiDefaultConfig\": {}");

    private static final DocRef DOC_REF = new DocRef(AnalyticRuleDoc.TYPE, "rule-1", "My Rule");

    // AnalyticRuleResource.fetch(): a scheduled rule
    private static final String DOC = """
            {
              "type": "AnalyticRule", "uuid": "rule-1", "name": "My Rule",
              "query": "from index\\nselect name, count\\n",
              "timeRange": {"name": "All time"},
              "description": "# Rule docs",
              PROCESS_TYPE"notifications": [],
              "duplicateNotificationConfig": {"rememberNotifications": true, "chooseColumns": false,
                "columnNames": []}
            }""";

    private static final String SCHEDULED = "\"analyticProcessType\": \"SCHEDULED_QUERY\", ";

    // A table builder rule that runs on node9
    private static final String TABLE_BUILDER = """
            "analyticProcessType": "TABLE_BUILDER",
              "analyticProcessConfig": {"type": "table_builder", "enabled": true, "node": "node9"},\s""";

    // AnalyticProcessResource.getTracker(): the table builder's progress
    private static final String TRACKER = """
            {"analyticUuid": "rule-1", "analyticTrackerData": {"type": "table_builder",
              "lastStreamCount": 3, "lastStreamId": 77, "totalStreamCount": 12, "totalEventCount": 4321}}""";

    // The duplicate rows: two columns and two rows
    private static final String DUPLICATES = """
            {"columnNames": ["host", "user"],
              "resultPage": {"values": [{"values": ["alpha", "bob"]}, {"values": ["beta", "carol"]}],
                "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}}""";

    private static final RestFixtures FIXTURES = fixtures(DOC.replace("PROCESS_TYPE", SCHEDULED));

    // The table builder's progress requests so far: the first is answered with TRACKER, the
    // next (Refresh) with more events processed
    private static int trackerRequests;

    private static final RestFixtures TABLE_BUILDER_FIXTURES = analyticRoutes(RestFixtures.builder()
            .get("/analyticRule/v1/rule-1", RestReply.json(DOC.replace("PROCESS_TYPE", TABLE_BUILDER)))
            .route(RequestMatcher.post("/analyticProcess/v1/tracker"), request -> {
                trackerRequests++;
                return RestReply.json(trackerRequests == 1
                        ? TRACKER
                        : TRACKER.replace("4321", "5000"));
            })).build();

    // NewRuleHidesTypeTabs: no process type
    private static final RestFixtures NEW_RULE_FIXTURES = fixtures(DOC.replace("PROCESS_TYPE", ""));

    // The Shards story's spy of its shard data requests, and its other query table: its play can't
    // reach its render's locals, so the story being shown sets them
    private static final String SHARD_DATA = "shardData";
    private static Runnable onShardData = () -> {
    };
    private static QueryResultTableController otherTable;

    private static final RestFixtures SHARD_FIXTURES = analyticRoutes(RestFixtures.builder()
            .get("/analyticRule/v1/rule-1", RestReply.json(DOC.replace("PROCESS_TYPE", SCHEDULED)))
            .route(RequestMatcher.post("/analyticDataShard/v1/getData"), request -> {
                onShardData.run();
                return RestReply.json(QueryEditorStories.TABLE);
            })).build();

    private AnalyticRuleEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/AnalyticRuleEditor", AnalyticRuleEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A scheduled query rule has Duplicate Management, not Shards
                .story("Default", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    for (final String label : new String[]{"Query", "Notifications", "Execution",
                            "Duplicate Management", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.expect(DocumentEditors.tab(play, "Shards")).not().toBeVisible();
                    play.expect(play.getByRole("button", "Execute Query")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // A new rule (no process type) has neither type's tab
                .story("NewRuleHidesTypeTabs", context -> render(context, NEW_RULE_FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocumentEditors.tab(play, "Execution")).toBeInTheDocument());
                    play.expect(DocumentEditors.tab(play, "Shards")).not().toBeVisible();
                    play.expect(DocumentEditors.tab(play, "Duplicate Management")).not().toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // Choosing Table Builder on the Execution tab swaps the type's tabs
                .story("ProcessTypeDrivesTabs", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Execution");
                    // The process type is a selection box's text box
                    play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
                    play.click(play.getByDisplayValue("Scheduled Query"));
                    play.click(screen.findByText("Table Builder", ".selection-popup *"));
                    play.waitFor(() -> play.expect(DocumentEditors.tab(play, "Shards")).toBeVisible());
                    play.expect(DocumentEditors.tab(play, "Duplicate Management")).not().toBeVisible();
                    play.waitFor(() -> play.expect(play.getByText("Processing Info")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // A table builder rule's Shards tab lists the shards found on every node, each node's
                // own shard at a path another node also has
                .story("Shards", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Execution");
                    play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
                    play.click(play.getByDisplayValue("Scheduled Query"));
                    play.click(screen.findByText("Table Builder", ".selection-popup *"));
                    play.waitFor(() -> play.expect(DocumentEditors.tab(play, "Shards")).toBeVisible());
                    openTab(play, "Shards");
                    play.waitFor(() -> play.expect(play.getAllByText("rule-1/2023-11-14").count()).toBe(2));
                    play.expect(play.getByText("rule-1/2023-11-15")).toBeInTheDocument();
                    play.expect(play.getByText("node9")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A shard's data is shown in the Shards tab's table (which once had no table
                // preferences to read, and threw), and refreshed for its own table, not when another
                // query's table (with no search of its own) is asked to refresh: the request went
                // over the app's bus, so every open rule's Shards tab fetched its data again
                .story("ShardsRefreshOnlyTheirOwn", AnalyticRuleEditorStories::renderShards)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Execution");
                    play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
                    play.click(play.getByDisplayValue("Scheduled Query"));
                    play.click(screen.findByText("Table Builder", ".selection-popup *"));
                    play.waitFor(() -> play.expect(DocumentEditors.tab(play, "Shards")).toBeVisible());
                    openTab(play, "Shards");
                    play.click(play.findByText("rule-1/2023-11-15"));
                    play.waitFor(() -> play.expect(play.spy(SHARD_DATA)).toHaveBeenCalledTimes(1));
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    expectNoProblems(play);
                    play.run("Another query's table asks to be refreshed", () -> otherTable.refresh());
                    play.sleep(200);
                    play.expect(play.spy(SHARD_DATA)).toHaveBeenCalledTimes(1);
                    expectNoProblems(play);
                })
                // Duplicate Management shows the columns of the duplicate checks found
                .story("DuplicateManagement", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    openTab(play, "Duplicate Management");
                    play.waitFor(() -> play.expect(play.getByText("host")).toBeInTheDocument());
                    play.expect(play.getByText("user")).toBeInTheDocument();
                    play.expect(play.getByText("alpha")).toBeInTheDocument();
                    play.expect(play.getByText("carol")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Adding a notification opens its dialog; OK adds a row to the list
                .story("AddNotification", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "New Notification"));
                    // The dialog is captioned 'Add Notification'
                    final Play dialog = dialog(screen, "Add Notification");
                    play.expect(dialog.getByText("Destination Type")).toBeInTheDocument();
                    // A new notification defaults to Stream (it once had no destination type, and OK
                    // added a row with none)
                    play.expect(destinationType(dialog)).toHaveValue("Stream");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("Stream", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    expectNoProblems(play);
                })
                // A stream destination's feed picker, declared in its template, has its controller
                // attached: it shows that no feed is chosen, and opens a dialog to choose one
                .story("StreamDestinationFeedPicker", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "New Notification"));
                    final Play dialog = dialog(screen, "Add Notification");
                    final Query picker = dialog.querySelector(".drop-down");
                    play.waitFor(() -> play.expect(picker).toHaveTextContent("None"));
                    play.expect(picker).toHaveAttribute("role", "button");
                    play.expect(picker).toHaveAttribute("aria-haspopup", "dialog");
                    play.expect(dialog.getByRole("button", StroomDom.button("Set Default")))
                            .toHaveAttribute("title", "Set as the default destination feed for all users");
                    expectNoProblems(play);
                })
                // The notification dialog: its maximum is always shown, and switching the
                // destination type back keeps what was typed
                .story("NotificationDialogDetails", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "New Notification"));
                    final Play dialog = dialog(screen, "Add Notification");
                    // It is labelled 'Maximum Notifications'
                    play.expect(dialog.getByText("Maximum Notifications")).toBeInTheDocument();
                    pickDestinationType(play, dialog, "Email");
                    play.waitFor(() -> play.expect(toField(dialog)).toBeInTheDocument());
                    play.type(toField(dialog), "ops@example.com");
                    pickDestinationType(play, dialog, "Stream");
                    pickDestinationType(play, dialog, "Email");
                    play.waitFor(() -> play.expect(toField(dialog)).toHaveValue("ops@example.com"));
                    expectNoProblems(play);
                })
                // Resume Notifications After starts at a day, and may be Never, which keeps sending
                // stopped until Stroom restarts. Before, it couldn't be none, though none means never
                .story("ResumeAfterNever", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "New Notification"));
                    final Play dialog = dialog(screen, "Add Notification");
                    // Resume applies only while notifications are limited
                    play.click(dialog.getByRole("checkbox", "Limit Notifications"));
                    final Play resume = dialog.within(dialog.getByRole("group", "Resume Notifications After"));
                    play.expect(resume.getByRole("spinbutton", "Amount")).toHaveValue("1");
                    play.expect(resume.getByLabelText("Unit")).toHaveValue("Days");
                    play.click(resume.getByLabelText("Unit"));
                    play.click(screen.findByText("Never", ".selection-popup *"));
                    play.waitFor(() -> play.expect(resume.getByLabelText("Unit")).toHaveValue("Never"));
                    play.expect(resume.getByRole("spinbutton", "Amount")).toBeDisabled();
                    expectNoProblems(play);
                })
                // Maximum and Resume Notifications After are enabled only while Limit Notifications
                // is ticked; they stayed enabled when it wasn't, although then unused
                .story("LimitGatesMaximumAndResume", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "New Notification"));
                    final Play dialog = dialog(screen, "Add Notification");
                    final Query limit = dialog.getByRole("checkbox", "Limit Notifications");
                    final Query maximum = dialog.getByRole("spinbutton", "Maximum Notifications");
                    final Play resume = dialog.within(dialog.getByRole("group", "Resume Notifications After"));
                    final Query amount = resume.getByRole("spinbutton", "Amount");
                    final Query unit = resume.getByLabelText("Unit");
                    // A new notification is not limited
                    play.expect(limit).not().toBeChecked();
                    play.expect(maximum).toBeDisabled();
                    play.expect(amount).toBeDisabled();
                    play.expect(unit).toBeDisabled();
                    play.click(limit);
                    play.expect(maximum).toBeEnabled();
                    play.expect(amount).toBeEnabled();
                    play.expect(unit).toBeEnabled();
                    play.click(limit);
                    play.expect(maximum).toBeDisabled();
                    play.expect(amount).toBeDisabled();
                    play.expect(unit).toBeDisabled();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectNoProblems(play);
                })
                // An email notification's subject template is tested on the server, which shows its
                // output in an alert
                .story("EmailTestTemplate", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "New Notification"));
                    final Play dialog = dialog(screen, "Add Notification");
                    pickDestinationType(play, dialog, "Email");
                    final Play subject = dialog.within(dialog.findByText("Email Subject Template", "label")
                            .closest(".form-group"));
                    play.click(subject.getByRole("button", "Test Template"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/analyticRule/v1/testTemplate")
                                    .withJsonBodyContaining("{\"string\": \"Detection: {{ ruleName }}\"}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Example template output:"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Table preferences are kept on the rule: hiding a column makes it dirty
                .story("TablePreferencesPersistOnTheDoc", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(AnalyticRuleEditorStories::hideCountColumn)
                // A new schedule's node is the configured default node
                .story("NewScheduleSeedsTheDefaultNode", context -> render(context, FIXTURES, NODE9_UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Execution");
                    // The button is titled 'New Execution Schedule'
                    play.click(play.findByRole("button", "New Execution Schedule"));
                    play.waitFor(() -> play.expect(screen.getByDisplayValue("node9")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // Settings, editable: the partner of SettingsReadOnly
                .story("SettingsEditable", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> showSettings(play, false))
                // Settings, read only: the partner of SettingsEditable
                .story("SettingsReadOnly", context -> render(context, FIXTURES, UI_CONFIG, DocumentPermission.VIEW))
                .withPlay(play -> showSettings(play, true))
                // Notifications, editable: the partner of NotificationsReadOnly
                .story("NotificationsEditable", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> showNotifications(play, false))
                // Notifications, read only: the partner of NotificationsEditable
                .story("NotificationsReadOnly", context -> render(context, FIXTURES, UI_CONFIG,
                        DocumentPermission.VIEW))
                .withPlay(play -> showNotifications(play, true))
                // Execution (a scheduled query), editable: the partner of ExecutionReadOnly
                .story("ExecutionEditable", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> showExecution(play, false))
                // Execution (a scheduled query), read only: the partner of ExecutionEditable
                .story("ExecutionReadOnly", context -> render(context, FIXTURES, UI_CONFIG, DocumentPermission.VIEW))
                .withPlay(play -> showExecution(play, true))
                // Execution (a table builder), editable: its settings and progress are shown, and
                // Refresh fetches the progress again; the partner of TableBuilderReadOnly
                .story("TableBuilderEditable", context -> render(context, TABLE_BUILDER_FIXTURES, UI_CONFIG))
                .withPlay(play -> showTableBuilder(play, false))
                // Execution (a table builder), read only: the partner of TableBuilderEditable
                .story("TableBuilderReadOnly", context -> render(context, TABLE_BUILDER_FIXTURES, UI_CONFIG,
                        DocumentPermission.VIEW))
                .withPlay(play -> showTableBuilder(play, true))
                // Duplicate Management, read only: the partner of DuplicateManagement
                .story("DuplicateManagementReadOnly", context -> render(context, FIXTURES, UI_CONFIG,
                        DocumentPermission.VIEW))
                .withPlay(play -> {
                    openTab(play, "Duplicate Management");
                    play.waitFor(() -> play.expect(play.getByText("host")).toBeInTheDocument());
                    play.expect(play.getByText("carol")).toBeInTheDocument();
                    expectReadOnlyNote(play, true);
                    expectNoProblems(play);
                });
    }

    /// Adds the routes of an analytic rule's or report's editor: the query pane's (a complete
    /// search of the QueryEditor's table), the Execution tab's schedules (none), the duplicate
    /// checks and the Permissions tab's.
    ///
    /// @param builder The story's own routes (its document).
    /// @return The builder.
    static RestFixtures.Builder analyticRoutes(final RestFixtures.Builder builder) {
        return QueryFixtures.editorRoutes(DocumentEditors.ownerPermissions(builder
                .route(QueryFixtures.SEARCH, RestReply.json(QueryFixtures.response(true, QueryEditorStories.TABLE)))
                .route(RequestMatcher.post("/analyticDataShard/v1/find").withQuery("nodeName=node1"),
                        RestReply.json(NODE1_SHARDS))
                .route(RequestMatcher.post("/analyticDataShard/v1/find").withQuery("nodeName=node9"),
                        RestReply.json(NODE9_SHARDS))
                .route(RequestMatcher.post("/duplicateCheck/v1/find"), RestReply.json(DUPLICATES))
                .route(RequestMatcher.post("/analyticRule/v1/testTemplate"),
                        RestReply.json("{\"string\": \"Detection: My Rule\"}"))
                .route(RequestMatcher.post("/executionSchedule/v1/fetchExecutionSchedule"),
                        RestReply.json(EMPTY_PAGE))
                .route(RequestMatcher.post("/scheduledTime/v1"), RestReply.json("{\"nextScheduledTimeMs\": 0}"))
                .route(RequestMatcher.get("/node/v1/all"), RestReply.json("[\"node1\", \"node9\"]"))
                .route(RequestMatcher.get("/node/v1/enabled"), RestReply.json("[\"node1\", \"node9\"]"))));
    }

    private static final String EMPTY_PAGE =
            "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, \"exact\": true}}";

    // A table builder rule's shards, found on each node; both nodes have a shard at the same path
    private static final String NODE1_SHARDS = """
            {"values": [
              {"node": "node1", "path": "rule-1/2023-11-14", "createTimeMs": 1700000000000, "size": 2048},
              {"node": "node1", "path": "rule-1/2023-11-15", "createTimeMs": 1700086400000, "size": 1048576}
            ], "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";
    private static final String NODE9_SHARDS = """
            {"values": [
              {"node": "node9", "path": "rule-1/2023-11-14", "createTimeMs": 1700000000000, "size": 4096}
            ], "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    /// Runs the query, hides the Count column and checks that the document is dirty, i.e. that
    /// the table preferences are part of it. The play can't see the document the editor would
    /// save, so the check is that Save is enabled.
    ///
    /// @param play The play.
    static void hideCountColumn(final Play play) {
        final Play screen = play.screen();
        play.click(play.findByRole("button", "Execute Query"));
        play.findByText("alpha");
        play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
        // Stroom opens a column's menu when its header is clicked
        StroomDom.clickHeader(play, play.getByText("Count", ".column-header__label"));
        play.click(screen.findByText("Hide Column", StroomDom.MENU_ITEM_TEXT));
        play.waitFor(() -> play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled"));
        // The preference is also sent with the next search
        play.click(play.getByRole("button", "Execute Query"));
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                QueryFixtures.SEARCH.withJsonBodyContaining("{\"queryTablePreferences\": {\"columns\": "
                                                            + "[{}, {\"name\": \"Count\", \"visible\": false}, "
                                                            + "{}, {}]}}").toSpyMatcher()));
        expectNoProblems(play);
    }

    /// Finds a dialog by its caption.
    ///
    /// @param screen  The page's body.
    /// @param caption The dialog's caption.
    /// @return The dialog.
    static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    private static Query toField(final Play dialog) {
        return dialog.within(dialog.getByText("To", "label").closest(".form-group")).querySelector("input, textarea");
    }

    // The destination type's selection box
    private static Query destinationType(final Play dialog) {
        return dialog.within(dialog.getByText("Destination Type", "label").closest(".form-group"))
                .querySelector(StroomDom.SELECTION_BOX);
    }

    private static void pickDestinationType(final Play play, final Play dialog, final String type) {
        play.click(destinationType(dialog));
        play.click(play.screen().findByText(type, ".selection-popup *"));
    }

    // Opens the Settings tab and waits for its fields
    private static void showSettings(final Play play, final boolean readOnly) {
        openTab(play, "Settings");
        play.waitFor(() -> play.expect(play.getByText("Feed For Errors", "label")).toBeInTheDocument());
        play.expect(play.getByRole("checkbox", "Include Rule Documentation")).toBeInTheDocument();
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // Opens the Notifications tab and waits for its list
    private static void showNotifications(final Play play, final boolean readOnly) {
        openTab(play, "Notifications");
        play.waitFor(() -> play.expect(play.getByRole("button", "New Notification")).toBeInTheDocument());
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // Opens the Execution tab and waits for the process type
    private static void showExecution(final Play play, final boolean readOnly) {
        openTab(play, "Execution");
        play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
        play.waitFor(() -> play.expect(play.getByRole("button", "New Execution Schedule")).toBeInTheDocument());
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // The Execution tab of a table builder rule: its node, its tick and its progress, and Refresh
    private static void showTableBuilder(final Play play, final boolean readOnly) {
        final Play screen = play.screen();
        openTab(play, "Execution");
        play.waitFor(() -> play.expect(screen.getByDisplayValue("node9")).toBeInTheDocument());
        play.waitFor(() -> play.expect(play.getByText("4321")).toBeInTheDocument());
        play.expect(play.getByText("Total Streams Processed")).toBeInTheDocument();
        final Query enabled = play.getByRole("checkbox", "Enabled");
        play.expect(enabled).toBeChecked();
        if (readOnly) {
            play.expect(enabled).toHaveAttribute("aria-readonly", "true");
        } else {
            play.expect(enabled).not().toHaveAttribute("aria-readonly", "true");
        }
        play.click(play.getByRole("button", "Refresh"));
        play.waitFor(() -> play.expect(play.getByText("5000")).toBeInTheDocument());
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // A read-only document's tab says so
    private static void expectReadOnlyNote(final Play play, final boolean readOnly) {
        if (readOnly) {
            play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
        }
    }

    private static void openTab(final Play play, final String label) {
        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
        play.click(DocumentEditors.tab(play, label));
    }

    private static void expectNoProblems(final Play play) {
        DocumentEditors.expectNoProblems(play);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures, final String uiConfig) {
        return render(context, fixtures, uiConfig, DocumentPermission.EDIT);
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final String uiConfig,
                                 final DocumentPermission permission) {
        // Each story's table builder progress starts again at its first reply
        trackerRequests = 0;
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .uiConfig(uiConfig)
                .build();
        harness.getSecurityContext().setDocumentPermission(permission);
        final AnalyticRuleResource resource = GWT.create(AnalyticRuleResource.class);
        // Opened once Stroom has started, as AnalyticsPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getAnalyticRuleEditor(), DOC_REF,
                resource, res -> res.fetch(DOC_REF.getUuid())));
        return harness.asWidget();
    }

    // The Shards story's: a table builder rule whose shards' data replies are spied on
    private static Widget renderShards(final StoryContext context) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, SHARD_FIXTURES)
                .injector(injector)
                .uiConfig(UI_CONFIG)
                .build();
        harness.getSecurityContext().setDocumentPermission(DocumentPermission.EDIT);
        harness.fn(SHARD_DATA);
        onShardData = () -> harness.spy(SHARD_DATA, "getData");
        final AnalyticRuleResource resource = GWT.create(AnalyticRuleResource.class);
        harness.afterStartUp(() -> {
            DocumentEditors.open(harness, injector.getAnalyticRuleEditor(), DOC_REF,
                    resource, res -> res.fetch(DOC_REF.getUuid()));
            // Another query's table, not shown, as another open rule's Shards tab has
            final ResultTable resultTable = new ResultTable();
            resultTable.setTableName("Query Results");
            final QueryResultTableController table = injector.getQueryResultTable().attach(resultTable);
            harness.getComponentRoot().adopt(table.getControllerNode());
            otherTable = table;
        });
        return harness.asWidget();
    }

    private static RestFixtures fixtures(final String doc) {
        return analyticRoutes(RestFixtures.builder().get("/analyticRule/v1/rule-1", RestReply.json(doc))).build();
    }
}
