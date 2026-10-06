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

import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportResource;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/ReportEditor` in the React Storybook, showing Stroom's real
/// `ReportPresenter` (a report's editor tab: Query, Settings, Notifications, Execution,
/// Documentation and Permissions) with fake REST replies.
///
/// As `ReportPlugin` does, the story fetches the document (`GET /report/v1/{uuid}`), checks the
/// user may edit it and reads it into the editor. The query pane's requests are answered by
/// [QueryFixtures] (as for `App/Editors/QueryEditor`), and the Execution tab's schedules by
/// `POST /executionSchedule/v1/fetchExecutionSchedule` (none).
public final class ReportEditorStories {

    private static final DocRef DOC_REF = new DocRef(ReportDoc.TYPE, "report-1", "My Report");

    // ReportResource.fetch()
    private static final String DOC = """
            {
              "type": "Report", "uuid": "report-1", "name": "My Report",
              "query": "from index\\nselect name, count\\n",
              "timeRange": {"name": "All time"},
              "description": "# Report docs",
              "analyticProcessType": "SCHEDULED_QUERY",
              "notifications": [],
              ERROR_FEED"reportSettings": {"fileType": "EXCEL", "sendEmptyReports": true}
            }""";

    private static final String ERROR_FEED =
            "\"errorFeed\": {\"type\": \"Feed\", \"uuid\": \"feed-err\", \"name\": \"ERROR_FEED\"}, ";

    private static final RestFixtures FIXTURES = fixtures(DOC.replace("ERROR_FEED", ERROR_FEED));

    // NotificationsRevealDoesNotDirty: no error feed
    private static final RestFixtures NO_ERROR_FEED_FIXTURES = fixtures(DOC.replace("ERROR_FEED", ""));

    private ReportEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/ReportEditor", ReportEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The report's tabs: no Shards or Duplicate Management
                .story("Default", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    for (final String label : new String[]{
                            "Query", "Settings", "Notifications", "Execution", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.expect(play.queryByText("Shards", StroomDom.LINK_TAB_LABEL)).toBeNull();
                    play.expect(play.queryByText("Duplicate Management", StroomDom.LINK_TAB_LABEL)).toBeNull();
                    play.expect(play.getByRole("button", "Execute Query")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // Settings: the file type (Excel) and Send Empty Reports; changing the file type
                // makes the document dirty
                .story("Settings", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Settings");
                    play.waitFor(() -> play.expect(play.getByText("File Type", "label")).toBeInTheDocument());
                    final Query fileType = fileType(play);
                    play.expect(fileType).toHaveValue("Excel");
                    play.expect(play.getByText("Send Empty Reports")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    play.click(fileType);
                    play.click(screen.findByText("Markdown", ".SelectionPopup *"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save")).not().toHaveClass("disabled"));
                    DocumentEditors.expectNoProblems(play);
                })
                // A report has no 'Include Rule Documentation'; adding a notification shows a
                // Stream row
                .story("NotificationsNoIncludeDoc", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: GWT's 'Feed For Errors' (and a rule's 'Include Rule
                    // Documentation') are on the Settings tab, not the Notifications tab
                    openTab(play, "Settings");
                    play.waitFor(() -> play.expect(play.getByText("Feed For Errors")).toBeInTheDocument());
                    play.expect(play.queryByText(TextMatch.containingIgnoreCase("include rule documentation")))
                            .toBeNull();
                    openTab(play, "Notifications");
                    play.click(play.findByRole("button", "Add Notification"));
                    // Differs from React: GWT's dialog is captioned 'Add Notification', and a new
                    // notification has no destination type, so the play chooses Stream
                    final Play dialog = AnalyticRuleEditorStories.dialog(screen, "Add Notification");
                    play.click(dialog.within(dialog.getByText("Destination Type", "label").closest(".form-group"))
                            .querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Stream", ".SelectionPopup *"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("Stream", "td *")).toBeInTheDocument());
                    DocumentEditors.expectNoProblems(play);
                })
                // Opening the Notifications tab doesn't make the document dirty
                .story("NotificationsRevealDoesNotDirty", context -> render(context, NO_ERROR_FEED_FIXTURES))
                .withPlay(play -> {
                    play.expect(play.findByRole("button", "Save")).toHaveClass("disabled");
                    openTab(play, "Notifications");
                    play.waitFor(() -> play.expect(play.getByRole("button", "Add Notification")).toBeInTheDocument());
                    // Differs from React: 'Feed For Errors' is on GWT's Settings tab, so the play
                    // shows that too
                    openTab(play, "Settings");
                    play.waitFor(() -> play.expect(play.getByText("Feed For Errors")).toBeInTheDocument());
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // The Execution tab offers only Scheduled Query
                .story("ExecutionScheduledOnly", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Execution");
                    play.waitFor(() -> play.expect(play.getByDisplayValue("Scheduled Query")).toBeInTheDocument());
                    play.click(play.getByDisplayValue("Scheduled Query"));
                    play.expect(screen.queryByText("Streaming")).toBeNull();
                    play.expect(screen.queryByText("Table Builder")).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // Table preferences are kept on the document: hiding a column makes it dirty
                .story("TablePreferencesPersistOnTheDoc", context -> render(context, FIXTURES))
                .withPlay(play -> AnalyticRuleEditorStories.hideCountColumn(play));
    }

    private static RestFixtures fixtures(final String doc) {
        return AnalyticRuleEditorStories.analyticRoutes(DocumentEditors.decorated(RestFixtures.builder()
                                .get("/report/v1/report-1", RestReply.json(doc)),
                        "{\"type\": \"Feed\", \"uuid\": \"feed-err\", \"name\": \"ERROR_FEED\"}"))
                .build();
    }

    private static void openTab(final Play play, final String label) {
        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
        play.click(DocumentEditors.tab(play, label));
    }

    // The File Type selection box
    private static Query fileType(final Play play) {
        return play.within(play.getByText("File Type", "label").closest(".form-group"))
                .querySelector(StroomDom.SELECTION_BOX);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .uiConfig(AnalyticRuleEditorStories.UI_CONFIG)
                .build();
        harness.getSecurityContext().setDocumentPermission(DocumentPermission.EDIT);
        final ReportResource resource = GWT.create(ReportResource.class);
        // Opened once Stroom has started, as ReportPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getReportPresenter(), DOC_REF,
                resource, res -> res.fetch(DOC_REF.getUuid())));
        return harness.asWidget();
    }
}
