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

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
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
import stroom.statistics.impl.sql.shared.StatisticResource;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/StatisticStoreEditor` in the React Storybook, showing Stroom's
/// real `StatisticsDataSourcePresenter` (a statistic store's editor tab: Settings, Fields, Custom
/// Roll-ups, Documentation and Permissions) with fake REST replies.
///
/// As `StatisticsPlugin` does, the story fetches the document (`GET /statistic/v1/{uuid}`), checks
/// the user may edit it and reads it into the editor. React's `docPermission` seam is the
/// Permissions tab's `POST /permission/doc/v1/fetchDocumentUserPermissions`.
public final class StatisticStoreEditorStories {

    private static final DocRef DOC_REF = new DocRef(StatisticStoreDoc.TYPE, "stat-1", "My Statistic");

    // StatisticResource.fetch(): stored field order 'user', 'host'; the mask's position 0 is
    // 'user'. GWT sorts the fields to 'host', 'user'
    private static final String DOC = """
            {
              "type": "StatisticStore", "uuid": "stat-1", "name": "My Statistic",
              "statisticType": "COUNT", "rollUpType": "CUSTOM", "precision": 3600000, "enabled": true,
              "config": {
                "fields": [{"fieldName": "user"}, {"fieldName": "host"}],
                "customRollUpMasks": [{"rolledUpTagPosition": [0]}]
              },
              "description": "# Stat docs"
            }""";

    private static final RestFixtures FIXTURES = DocumentEditors.ownerPermissions(RestFixtures.builder())
            .get("/statistic/v1/stat-1", RestReply.json(DOC))
            .build();

    private StatisticStoreEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/StatisticStoreEditor", StatisticStoreEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    for (final String label : new String[]{
                            "Settings", "Fields", "Custom Roll-ups", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // The settings
                    play.waitFor(() -> play.expect(play.getByText("Statistic Type", "label")).toBeInTheDocument());
                    play.expect(play.getByText("Precision", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Roll Up Type", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Enabled")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // The Fields tab: the fields, sorted; adding one with the dialog makes the document
                // dirty
                .story("AddField", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Fields");
                    play.waitFor(() -> play.expect(play.getByText("host")).toBeInTheDocument());
                    play.expect(play.getByText("user")).toBeInTheDocument();
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.click(play.getByRole("button", "New Field"));
                    // Differs from React: the name's text box is identified as 'statisticsFieldName'
                    final Query input = nameInput(screen);
                    play.type(input, "app");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("app")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocumentEditors.expectNoProblems(play);
                })
                // The field dialog refuses a duplicate name with an alert, staying open
                .story("DuplicateFieldRejected", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Fields");
                    play.click(play.findByRole("button", "New Field"));
                    play.type(nameInput(screen), "host");
                    final Query ok = screen.getByRole("button", StroomDom.button("OK"));
                    play.expect(ok).not().toBeDisabled();
                    play.click(ok);
                    play.expect(screen.findByText("Another field with this name already exists"))
                            .toBeInTheDocument();
                    play.expect(screen.querySelector("#statisticsFieldName")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "ERROR: Another field with this name already exists");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The Fields tab is a grid: Edit/Remove need a selection, a double click edits and
                // Remove takes the selection without confirming
                .story("FieldGridEditAndRemove", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Fields");
                    play.expect(play.findByRole("button", "Edit Field")).toHaveClass("disabled");
                    play.expect(play.getByRole("button", "Remove Field")).toHaveClass("disabled");
                    play.dblClick(play.findByText("user"));
                    play.expect(nameInput(screen)).toHaveValue("user");
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.click(play.getByText("host"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Remove Field"))
                            .not().toHaveClass("disabled"));
                    play.click(play.getByRole("button", "Remove Field"));
                    play.waitFor(() -> play.expect(play.queryByText("host")).toBeNull());
                    play.expect(screen.queryByText(TextMatch.containingIgnoreCase("are you sure"))).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // Custom Roll-ups: a tick box column per field; a new permutation makes the
                // document dirty
                .story("CustomRollUps", context -> render(context, false))
                .withPlay(play -> {
                    openTab(play, "Custom Roll-ups");
                    // Differs from React: GWT's headers are <th> without a columnheader role
                    play.waitFor(() -> play.expect(play.getByText("host", "th, th *")).toBeInTheDocument());
                    play.expect(play.getByText("user", "th, th *")).toBeInTheDocument();
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.click(play.getByRole("button", "New roll-up permutation"));
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    play.expect(play.getByRole("button", "Remove roll-up permutation")).toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only: the field buttons say why they're disabled and Save can't be enabled
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    openTab(play, "Fields");
                    play.expect(play.findByRole("button", "New field disabled as fields are read only"))
                            .toHaveClass("disabled");
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // The roll-up grid always has the no roll-up row: removing the last permutation
                // puts it back
                .story("RemovingTheLastRollUpReSeedsIt", context -> render(context, false))
                .withPlay(play -> {
                    openTab(play, "Custom Roll-ups");
                    // Differs from React: GWT's rows are the grid's <tr>s
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(1));
                    play.click(play.querySelectorAll(StroomDom.GRID_ROW + " td").nth(0));
                    play.click(play.getByRole("button", "Remove roll-up permutation"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(1));
                    DocumentEditors.expectNoProblems(play);
                });
    }

    private static void openTab(final Play play, final String label) {
        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
        play.click(DocumentEditors.tab(play, label));
    }

    // The field dialog's name text box
    private static Query nameInput(final Play screen) {
        screen.waitFor(() -> screen.expect(screen.querySelector("#statisticsFieldName")).toBeInTheDocument());
        return screen.querySelector("#statisticsFieldName");
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .realAlerts()
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        final StatisticResource resource = GWT.create(StatisticResource.class);
        // Opened once Stroom has started, as StatisticsPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getStatisticsDataSourcePresenter(),
                DOC_REF, resource, res -> res.fetch(DOC_REF.getUuid())));
        return harness.asWidget();
    }
}
