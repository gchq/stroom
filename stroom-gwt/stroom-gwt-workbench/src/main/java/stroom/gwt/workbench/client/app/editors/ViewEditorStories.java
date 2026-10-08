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
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.DocumentPermission;
import stroom.view.client.presenter.ViewPresenter;
import stroom.view.shared.ViewDoc;
import stroom.view.shared.ViewResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/ViewEditor` in the React Storybook, showing Stroom's real
/// [ViewPresenter] (a View's editor tab: Settings, Documentation and Permissions) with fake REST
/// replies.
///
/// The React story passes the document and its seams as props; in Stroom, `ViewPlugin` fetches the
/// document (`GET /view/v1/{uuid}`), checks the user may edit it and reads it into the editor, which
/// the story does as the plugin does. React's `docPermission` seam is the Permissions tab's
/// `POST /permission/doc/v1/fetchDocumentUserPermissions`; its empty `loadNodes` is never called
/// (the doc pickers aren't opened), nor is the explorer's.
///
/// React's stateful harness (Save enabling as the document changes) is Stroom's own
/// `DocTabPresenter` here; no story saves.
public final class ViewEditorStories {

    private static final DocRef DOC_REF = new DocRef(ViewDoc.TYPE, "view-1", "My View");

    // ViewResource.fetch()
    private static final String DOC = """
            {
              "type": "View", "uuid": "view-1", "name": "My View",
              "dataSource": {"type": "Feed", "uuid": "ds-1", "name": "My DataSource"},
              "pipeline": {"type": "Pipeline", "uuid": "pipe-1", "name": "My Pipeline"},
              "filter": {"type": "operator", "op": "AND", "enabled": true, "children": []},
              "description": "# View docs"
            }""";

    private static final RestFixtures FIXTURES = DocumentEditors.decorated(
                    DocumentEditors.ownerPermissions(RestFixtures.builder()),
                    "{\"type\": \"Feed\", \"uuid\": \"ds-1\", \"name\": \"My DataSource\"}",
                    "{\"type\": \"Pipeline\", \"uuid\": \"pipe-1\", \"name\": \"My Pipeline\"}")
            .get("/view/v1/view-1", RestReply.json(DOC))
            .build();

    private ViewEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/ViewEditor", ViewEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    // GWT ViewPresenter tabs: Settings (default), Documentation and Permissions
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // Settings shows the three GWT groups and the selected data source and pipeline.
                    // Differs from React: the groups' captions are FormGroup labels
                    play.expect(play.getByText("Data Source", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Pipeline", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Meta Filter")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.getByText("My DataSource")).toBeInTheDocument());
                    play.expect(play.getByText("My Pipeline")).toBeInTheDocument();
                    // Each picker is a button that opens a dialog, named by its label then its value
                    final Query dataSource = play.getByRole("button",
                            TextMatch.startingWith("Data Source My DataSource"));
                    play.expect(dataSource).toHaveAttribute("aria-haspopup", "dialog");
                    play.expect(play.getByRole("button", TextMatch.startingWith("Pipeline My Pipeline")))
                            .toHaveAttribute("aria-haspopup", "dialog");
                    // Save starts disabled (clean on load)
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // Editing the Meta Filter expression (Add Term) makes the document dirty, so Save
                // is enabled
                .story("AddTermEnablesSave", context -> render(context, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.click(play.getByTitle("Add Term"));
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only: GWT disables the settings and Save can never be enabled
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("My DataSource")).toBeInTheDocument());
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    // The settings can't be changed (they once could, and the edits were thrown away)
                    play.expect(play.getByRole("button", TextMatch.startingWith("Data Source My DataSource")))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", TextMatch.startingWith("Pipeline My Pipeline")))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Add Term")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Add Operator")).toHaveAttribute("aria-disabled", "true");
                    DocumentEditors.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        final ViewResource resource = GWT.create(ViewResource.class);
        // Opened once Stroom has started, as ViewPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getViewPresenter(), DOC_REF,
                resource, res -> res.fetch(DOC_REF.getUuid())));
        return harness.asWidget();
    }
}
