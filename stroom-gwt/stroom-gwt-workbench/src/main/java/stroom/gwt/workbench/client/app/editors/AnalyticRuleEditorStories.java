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
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/AnalyticRuleEditor` in the React Storybook, showing Stroom's real
/// `AnalyticRulePresenter` (an analytic rule's editor tab: Query, Settings, Notifications,
/// Execution, Shards or Duplicate Management, Documentation and Permissions) with fake REST
/// replies.
///
/// As `AnalyticsPlugin` does, the story fetches the document (`GET /analyticRule/v1/{uuid}`),
/// checks the user may edit it and reads it into the editor. The query pane's requests are
/// answered by [QueryFixtures] (as for `App/Editors/QueryEditor`). React's `AnalyticRuleApi`,
/// `ExecutionScheduleApi` and `docPermission` seams are Stroom's analytic rule, execution
/// schedule, duplicate check and document permission endpoints.
public final class AnalyticRuleEditorStories {

    /// React's `appApiFixture.fetchUiConfig`: the analytic rules' defaults.
    static final String UI_CONFIG = QueryFixtures.uiConfigWith("""
            "analyticUiDefaultConfig": {"defaultNode": "node1",
              "defaultSubjectTemplate": "Detection: {{ ruleName }}",
              "defaultBodyTemplate": "A detection occurred."},
            "reportUiDefaultConfig": {}""");

    // NewScheduleSeedsTheDefaultNode: the default node is node9
    private static final String NODE9_UI_CONFIG = QueryFixtures.uiConfigWith(
            "\"analyticUiDefaultConfig\": {\"defaultNode\": \"node9\"}, \"reportUiDefaultConfig\": {}");

    private static final DocRef DOC_REF = new DocRef(AnalyticRuleDoc.TYPE, "rule-1", "My Rule");

    // AnalyticRuleResource.fetch(): React's SCHEDULED_DOC
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

    // React's findDuplicateRows: two columns and two rows
    private static final String DUPLICATES = """
            {"columnNames": ["host", "user"],
              "resultPage": {"values": [{"values": ["alpha", "bob"]}, {"values": ["beta", "carol"]}],
                "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}}""";

    private static final RestFixtures FIXTURES = fixtures(DOC.replace("PROCESS_TYPE", SCHEDULED));

    // NewRuleHidesTypeTabs: no process type
    private static final RestFixtures NEW_RULE_FIXTURES = fixtures(DOC.replace("PROCESS_TYPE", ""));

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
                    // Differs from React: the process type is a selection box's text box
                    play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
                    play.click(play.getByDisplayValue("Scheduled Query"));
                    play.click(screen.findByText("Table Builder", ".SelectionPopup *"));
                    play.waitFor(() -> play.expect(DocumentEditors.tab(play, "Shards")).toBeVisible());
                    play.expect(DocumentEditors.tab(play, "Duplicate Management")).not().toBeVisible();
                    play.waitFor(() -> play.expect(play.getByText("Processing Info")).toBeInTheDocument());
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
                    play.click(play.findByRole("button", "Add Notification"));
                    // Differs from React: GWT's dialog is captioned 'Add Notification'
                    final Play dialog = dialog(screen, "Add Notification");
                    play.expect(dialog.getByText("Destination Type")).toBeInTheDocument();
                    // A new notification defaults to Stream (it once had no destination type, and OK
                    // added a row with none)
                    play.expect(destinationType(dialog)).toHaveValue("Stream");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("Stream", "td *")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The notification dialog: its maximum is always shown, and switching the
                // destination type back keeps what was typed
                .story("NotificationDialogDetails", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "Add Notification"));
                    final Play dialog = dialog(screen, "Add Notification");
                    // Differs from React: GWT labels it 'Maximum Notifications'
                    play.expect(dialog.getByText("Maximum Notifications")).toBeInTheDocument();
                    pickDestinationType(play, dialog, "Email");
                    play.waitFor(() -> play.expect(toField(dialog)).toBeInTheDocument());
                    play.type(toField(dialog), "ops@example.com");
                    pickDestinationType(play, dialog, "Stream");
                    pickDestinationType(play, dialog, "Email");
                    play.waitFor(() -> play.expect(toField(dialog)).toHaveValue("ops@example.com"));
                    expectNoProblems(play);
                })
                // Table preferences are kept on the rule: hiding a column makes it dirty
                .story("TablePreferencesPersistOnTheDoc", context -> render(context, FIXTURES, UI_CONFIG))
                .withPlay(AnalyticRuleEditorStories::hideCountColumn)
                // A new schedule's node is the configured default node
                .story("NewScheduleSeedsTheDefaultNode", context -> render(context, FIXTURES, NODE9_UI_CONFIG))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Execution");
                    // Differs from React: GWT titles the button 'Add Execution Schedule'
                    play.click(play.findByRole("button", "Add Execution Schedule"));
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
                .route(RequestMatcher.post("/analyticDataShard/v1/find/" + RequestMatcher.PATH_WILDCARD),
                        RestReply.json(EMPTY_PAGE))
                .route(RequestMatcher.post("/duplicateCheck/v1/find"), RestReply.json(DUPLICATES))
                .route(RequestMatcher.post("/executionSchedule/v1/fetchExecutionSchedule"),
                        RestReply.json(EMPTY_PAGE))
                .route(RequestMatcher.post("/scheduledTime/v1"), RestReply.json("{\"nextScheduledTimeMs\": 0}"))
                .route(RequestMatcher.get("/node/v1/all"), RestReply.json("[\"node1\", \"node9\"]"))
                .route(RequestMatcher.get("/node/v1/enabled"), RestReply.json("[\"node1\", \"node9\"]"))));
    }

    private static final String EMPTY_PAGE =
            "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, \"exact\": true}}";

    /// Runs the query, hides the Count column and checks that the document is dirty, i.e. that
    /// the table preferences are part of it. Differs from React: the play can't see the
    /// document the editor would save, so the check is that Save is enabled.
    ///
    /// @param play The play.
    static void hideCountColumn(final Play play) {
        final Play screen = play.screen();
        play.click(play.findByRole("button", "Execute Query"));
        play.findByText("alpha");
        play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
        // Differs from React: GWT opens a column's menu when its header is clicked
        QueryEditorStories.clickHeader(play, play.getByText("Count", ".column-top .column-label"));
        play.click(screen.findByText("Hide", StroomDom.MENU_ITEM_TEXT));
        play.waitFor(() -> play.expect(play.getByRole("button", "Save")).not().toHaveClass("disabled"));
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
        play.click(play.screen().findByText(type, ".SelectionPopup *"));
    }

    // Opens the Settings tab and waits for its fields
    private static void showSettings(final Play play, final boolean readOnly) {
        openTab(play, "Settings");
        play.waitFor(() -> play.expect(play.getByText("Feed For Errors", "label")).toBeInTheDocument());
        play.expect(play.getByText("Include Rule Documentation", "label")).toBeInTheDocument();
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // Opens the Notifications tab and waits for its list
    private static void showNotifications(final Play play, final boolean readOnly) {
        openTab(play, "Notifications");
        play.waitFor(() -> play.expect(play.getByRole("button", "Add Notification")).toBeInTheDocument());
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // Opens the Execution tab and waits for the process type
    private static void showExecution(final Play play, final boolean readOnly) {
        openTab(play, "Execution");
        play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
        play.waitFor(() -> play.expect(play.getByRole("button", "Add Execution Schedule")).toBeInTheDocument());
        expectReadOnlyNote(play, readOnly);
        expectNoProblems(play);
    }

    // A read-only document's tab says so
    private static void expectReadOnlyNote(final Play play, final boolean readOnly) {
        if (readOnly) {
            play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
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
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .uiConfig(uiConfig)
                .build();
        harness.getSecurityContext().setDocumentPermission(permission);
        final AnalyticRuleResource resource = GWT.create(AnalyticRuleResource.class);
        // Opened once Stroom has started, as AnalyticsPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getAnalyticRulePresenter(), DOC_REF,
                resource, res -> res.fetch(DOC_REF.getUuid())));
        return harness.asWidget();
    }

    private static RestFixtures fixtures(final String doc) {
        return analyticRoutes(RestFixtures.builder().get("/analyticRule/v1/rule-1", RestReply.json(doc))).build();
    }
}
