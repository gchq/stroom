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

import stroom.datagen.client.presenter.DataGenPresenter;
import stroom.datagen.shared.DataGenDoc;
import stroom.datagen.shared.DataGenResource;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/DataGenEditor` in the React Storybook, showing Stroom's real
/// [DataGenPresenter] (a Data Generator's tab: Settings, Execution, Documentation and
/// Permissions) with fake REST replies.
///
/// As `DataGenPlugin` does, the story fetches the document (`GET /datagen/v1/{uuid}`) and reads it
/// into the editor ([DocEditors#open]). React's `scheduleApi` seam is Stroom's
/// `ExecutionScheduleResource`: `find` → `POST /executionSchedule/v1/fetchExecutionSchedule`,
/// `fetchHistory` → `fetchExecutionHistory`, `fetchTracker` → `fetchTracker`; `docPermission` →
/// the Permissions tab's routes.
public final class DataGenEditorStories {

    private static final DocRef DOC_REF = new DocRef(DataGenDoc.TYPE, "datagen-1", "My Generator");

    // DataGenResource.fetch(): React's DATAGEN_DOC
    private static final String DOC = """
            {"type": "DataGen", "uuid": "datagen-1", "name": "My Generator",
              "feed": {"type": "Feed", "uuid": "feed-1", "name": "My Feed"},
              "template": "line1\\nline2\\n", "description": "# DataGen docs"}""";

    // React's SCHEDULE
    private static final String SCHEDULE = """
            {"uuid": "sched-1", "name": "Nightly", "enabled": true, "nodeName": "node1",
              "schedule": {"type": "CRON", "expression": "0 0 0 * * ?"},
              "scheduleBounds": {"startTimeMs": 1700000000000},
              "owningDoc": {"type": "DataGen", "uuid": "datagen-1", "name": "My Generator"}}""";

    private static final String SCHEDULES = """
            {"values": [SCHEDULE], "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""
            .replace("SCHEDULE", SCHEDULE);

    private static final String HISTORY = """
            {"values": [{"id": 1, "executionTimeMs": 1700000000000, "effectiveExecutionTimeMs": 1699999000000,
                "status": "Complete", "message": "ok", "executionSchedule": SCHEDULE}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""
            .replace("SCHEDULE", SCHEDULE);

    private static final RestFixtures FIXTURES = DocEditors.docSelectionRoutes(
                    DocEditors.permissionRoutes(RestFixtures.builder()))
            .get("/datagen/v1/datagen-1", RestReply.json(DOC))
            .put("/datagen/v1/datagen-1", request -> RestReply.json(request.getBody()))
            .post("/executionSchedule/v1/fetchExecutionSchedule", RestReply.json(SCHEDULES))
            .post("/executionSchedule/v1/fetchExecutionHistory", RestReply.json(HISTORY))
            // NodeResource.listAllNodes(): React's listNodes
            .get("/node/v1/all", RestReply.json("[\"node1\", \"node2\"]"))
            .post("/scheduledTime/v1", RestReply.json("{\"nextScheduledTimeMs\": 1700086400000}"))
            .post("/executionSchedule/v1/fetchTracker",
                    RestReply.json("{\"lastEffectiveExecutionTimeMs\": 1700000000000}"))
            .build();

    // Differs from React: Stroom's Ace editor's text area, which React's port copies
    private static final String ACE_INPUT = ".ace_text-input";

    private DataGenEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/DataGenEditor", DataGenEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Execution", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    waitForSettings(play);
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Settings, read only: the partner of Default
                .story("SettingsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    waitForSettings(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The Execution tab lists the document's schedules; Add opens the Create dialog
                .story("Execution", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Execution")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Execution"));
                    play.waitFor(() -> play.expect(play.getByText("Nightly")).toBeInTheDocument());
                    // Differs from React: there is no 'Execution history' heading; the history
                    // list is the second grid, headed by its columns
                    play.expect(play.getByText("Execution Time")).toBeInTheDocument();
                    // Differs from React: the button's name is its title, 'Add Execution Schedule'
                    play.click(play.getByRole("button", "Add Execution Schedule"));
                    play.waitFor(() -> play.expect(screen.getByText("Create Schedule", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // Selecting a schedule loads its run history
                .story("ExecutionHistory", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Execution")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Execution"));
                    play.click(play.findByText("Nightly"));
                    play.waitFor(() -> play.expect(play.getByText("Complete")).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).toHaveAttribute("readonly"));
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Execution, editable: the partner of ExecutionReadOnly
                .story("ExecutionEditable", context -> render(context, false))
                .withPlay(play -> {
                    showExecution(play);
                    DocEditors.expectNoProblems(play);
                })
                // Execution, read only: the partner of ExecutionEditable
                .story("ExecutionReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    showExecution(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // Waits for the Settings tab: the destination feed (selected) and the template's Ace editor
    private static void waitForSettings(final Play play) {
        play.waitFor(() -> play.expect(play.getByText("Destination Feed", "label")).toBeInTheDocument());
        play.expect(play.getByText("Template", "label")).toBeInTheDocument();
        play.waitFor(() -> play.expect(play.getByText("My Feed")).toBeInTheDocument());
        play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
    }

    // Opens the Execution tab and waits for its schedules and the (empty) history list
    private static void showExecution(final Play play) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, "Execution")).toBeInTheDocument());
        play.click(DocEditors.tab(play, "Execution"));
        play.waitFor(() -> play.expect(play.getByText("Nightly")).toBeInTheDocument());
        play.expect(play.getByText("Execution Time")).toBeInTheDocument();
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final DataGenResource resource = GWT.create(DataGenResource.class);
        return DocEditors.render(context, FIXTURES, readOnly,
                // The schedule dialog reads the analytic UI defaults, which Stroom always sends
                builder -> builder.uiConfig("{\"analyticUiDefaultConfig\": {}, "
                                            + StartupFixtures.DEFAULT_UI_CONFIG.trim().substring(1)),
                (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getDataGenPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
