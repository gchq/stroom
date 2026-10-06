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
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.visualisation.client.presenter.VisualisationPresenter;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.visualisation.shared.VisualisationResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories matching `App/Editors/VisualisationEditor` in the React Storybook, showing Stroom's
/// real [VisualisationPresenter] (a Visualisation's tab: Settings, Assets, Documentation and
/// Permissions) with fake REST replies.
///
/// As `VisualisationPlugin` does, the story fetches the document (`GET /visualisation/v1/{uuid}`)
/// and reads it into the editor ([DocEditors#open]); Save sends it to `PUT /visualisation/v1/{uuid}`
/// and then runs the editor's post save callback, which publishes the assets. React's
/// `assetsApi` seam is Stroom's `VisualisationAssetResource`: `fetchDraft` →
/// `GET /visualisationAssets/fetchDraftAssets/{uuid}`, `getContent` →
/// `GET /visualisationAssets/getContent/{uuid}?path=`, `saveToLive` →
/// `PUT /visualisationAssets/saveDraftToLive/{uuid}`; `loadNodes` → the script selection box's
/// explorer routes.
public final class VisualisationEditorStories {

    private static final DocRef DOC_REF = new DocRef(VisualisationDoc.TYPE, "vis-1", "My Vis");

    // VisualisationResource.fetch(): React's VIS_DOC
    private static final String DOC = """
            {"type": "Visualisation", "uuid": "vis-1", "name": "My Vis", "functionName": "D3.Bar",
              "scriptRef": {"type": "Script", "uuid": "script-1", "name": "My Script"},
              "settings": "{\\n  \\"tabs\\": []\\n}", "description": "# Vis docs"}""";

    // VisualisationAssetResource.fetchDraftAssets(): React's fetchDraft
    private static final String ASSETS = """
            {"ownerId": "vis-1", "dirty": true, "assets": [
                {"id": "a1", "path": "src", "folder": true},
                {"id": "a2", "path": "src/main.js", "folder": false},
                {"id": "a3", "path": "style.css", "folder": false}],
              "uploadedFiles": {}}""";

    private static final String SAVE_TO_LIVE = "/visualisationAssets/saveDraftToLive/vis-1";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (VisualisationSettingsViewImpl.ui.xml), not React's '<name>-input'
    private static final String FUNCTION_NAME = "#visualisationSettingsFunctionName";
    private static final String ASSET_NAME = "#visualisationAssetsName";

    // Differs from React: Stroom's Ace editor's text area, which React's port copies
    private static final String ACE_INPUT = ".ace_text-input";

    private VisualisationEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/VisualisationEditor", VisualisationEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Assets", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.waitFor(() -> play.expect(play.getByText("Function Name", "label")).toBeInTheDocument());
                    play.expect(play.getByText("Script", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Settings", "label")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.getByText("My Script")).toBeInTheDocument());
                    play.expect(play.querySelector(FUNCTION_NAME)).toHaveValue("D3.Bar");
                    // The settings JSON editor (Ace)
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(FUNCTION_NAME)).toHaveValue("D3.Bar"));
                    play.expect(save).toHaveClass("disabled");
                    play.type(play.querySelector(FUNCTION_NAME), "Chart");
                    // Differs from React: the text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // The Assets tab: a tree of the draft assets and an editor for the selected file
                .story("Assets", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Assets")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Assets"));
                    play.waitFor(() -> play.expect(play.getByText("style.css")).toBeInTheDocument());
                    play.expect(play.getByText("src")).toBeInTheDocument();
                    // Differs from React: the tree holds the collapsed folder's items, hidden
                    play.expect(play.getByText("main.js")).not().toBeVisible();
                    // Expand the folder, then open the file in the editor.
                    // Differs from React: GWT's Tree expands an item from its open/close image
                    play.click(play.within(play.getByText("src").closest("tr")).querySelector("img"));
                    play.waitFor(() -> play.expect(play.getByText("main.js")).toBeVisible());
                    play.click(play.getByText("main.js"));
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    // 'Add file' opens a menu (Add New Folder, Add New File, Upload File); 'Add New
                    // File' opens the name dialog
                    play.click(play.getByRole("button", "Add file"));
                    // Differs from React: the menu items have no menuitem role
                    final Value<List<String>> items = screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT).textContents();
                    play.waitFor(() -> play.expect("the menu's items", items)
                            .toEqual(List.of("Add New Folder", "Add New File", "Upload File")));
                    play.click(screen.getByText("Add New File", StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(screen.querySelector(ASSET_NAME)).not().toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(FUNCTION_NAME)).toBeDisabled());
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).toHaveAttribute("readonly"));
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Saving the document publishes the asset draft (the editor's post save callback
                // calls saveDraftToLive), and a false result is reported as 'Error saving assets'
                .story("AssetsPublishOnDocumentSave", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Assets")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Assets"));
                    // GWT's toolbar, with no Save button of its own
                    play.waitFor(() -> play.expect(play.getByRole("button", "Revert changes")).toBeInTheDocument());
                    play.expect(play.queryByRole("button", "Save assets to live")).toBeNull();
                    play.expect(play.queryByRole("button", "Revert assets to live")).toBeNull();

                    // Differs from React: React calls its publish helper directly; GWT's is the
                    // post save callback, so the play saves the document (after an edit) and checks
                    // the request, twice: the first publish succeeds, the second replies false
                    play.click(DocEditors.tab(play, "Settings"));
                    saveAfterEdit(play, "Chart");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(SAVE_TO_LIVE).toSpyMatcher()));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();

                    saveAfterEdit(play, "2");
                    play.waitFor(() -> play.expect(screen.getByText("Error saving assets")).toBeInTheDocument());
                    DocEditors.expectNoUnhandledRequests(play);
                });
    }

    // Edits the function name and saves the document
    private static void saveAfterEdit(final Play play, final String text) {
        play.type(play.findByDisplayValue(text.equals("Chart")
                ? "D3.Bar"
                : "D3.BarChart"), text);
        play.tab();
        final Query save = play.getByRole("button", "Save");
        play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
        play.click(save);
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final RestFixtures fixtures = DocEditors.docSelectionRoutes(
                        DocEditors.permissionRoutes(RestFixtures.builder()))
                .get("/visualisation/v1/vis-1", RestReply.json(DOC))
                .put("/visualisation/v1/vis-1", request -> RestReply.json(request.getBody()))
                .get("/visualisationAssets/fetchDraftAssets/vis-1", RestReply.json(ASSETS))
                .get("/visualisationAssets/getContent/vis-1", RestReply.json(
                        "{\"content\": \"// src/main.js\\nconsole.log(1);\", \"editorMode\": \"JAVASCRIPT\"}"))
                // React's saveToLive: true, then false for the second save
                .put(SAVE_TO_LIVE, RestReply.json("true"), RestReply.json("false"))
                .build();
        final VisualisationResource resource = GWT.create(VisualisationResource.class);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getVisualisationPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
