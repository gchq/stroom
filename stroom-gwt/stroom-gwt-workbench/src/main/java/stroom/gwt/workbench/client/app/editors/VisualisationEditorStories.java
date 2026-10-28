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
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.visualisation.client.component.VisualisationEditor;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.visualisation.shared.VisualisationResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The `App/Editors/VisualisationEditor` stories, showing Stroom's real [VisualisationEditor] (a
/// Visualisation's tab: Settings, Assets, Documentation and Permissions) with fake REST replies.
///
/// As `VisualisationPlugin` does, the story fetches the document (`GET /visualisation/v1/{uuid}`)
/// and reads it into the editor ([DocEditors#open]); Save sends it to
/// `PUT /visualisation/v1/{uuid}` and then runs the editor's post save callback, which publishes
/// the assets. The assets are Stroom's `VisualisationAssetResource`: the drafts
/// `GET /visualisationAssets/fetchDraftAssets/{uuid}`, a file's content
/// `GET /visualisationAssets/getContent/{uuid}?path=` and publishing
/// `PUT /visualisationAssets/saveDraftToLive/{uuid}`; the script selection box has its explorer
/// routes.
public final class VisualisationEditorStories {

    private static final DocRef DOC_REF = new DocRef(VisualisationDoc.TYPE, "vis-1", "My Vis");

    // VisualisationResource.fetch(): the visualisation
    private static final String DOC = """
            {"type": "Visualisation", "uuid": "vis-1", "name": "My Vis", "functionName": "D3.Bar",
              "scriptRef": {"type": "Script", "uuid": "script-1", "name": "My Script"},
              "settings": "{\\n  \\"tabs\\": []\\n}", "description": "# Vis docs"}""";

    // VisualisationAssetResource.fetchDraftAssets(): the draft assets
    private static final String ASSETS = """
            {"ownerId": "vis-1", "dirty": true, "assets": [
                {"id": "a1", "path": "src", "folder": true},
                {"id": "a2", "path": "src/main.js", "folder": false},
                {"id": "a3", "path": "style.css", "folder": false}],
              "uploadedFiles": {}}""";

    private static final String SAVE_TO_LIVE = "/visualisationAssets/saveDraftToLive/vis-1";

    // A FormGroup gives its control the group's identity as its id
    // (VisualisationSettings.ui.xml)
    private static final String FUNCTION_NAME = "#visualisationSettingsFunctionName";
    private static final String ASSET_NAME = "#visualisationAssetsName";
    private static final String NEW_FILE = "/visualisationAssets/updateNewFile/vis-1";
    private static final String RENAME = "/visualisationAssets/updateRename/vis-1";

    // Stroom's Ace editor's text area
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
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(FUNCTION_NAME)).toHaveValue("D3.Bar"));
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.type(play.querySelector(FUNCTION_NAME), "Chart");
                    // The text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
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
                    // A collapsed folder's items aren't shown
                    play.expect(play.queryByText("main.js")).toBeNull();
                    // Expand the folder from its expander, then open the file in the editor
                    play.click(play.within(play.getByText("src").closest("[role='treeitem']"))
                            .querySelector(".explorer-cell__expander"));
                    play.waitFor(() -> play.expect(play.getByText("main.js")).toBeVisible());
                    // Until a file is selected, the editor's area asks for one (it was blank)
                    final Query placeholder = play.getByText("Select a file to view or edit it");
                    play.expect(placeholder).toBeVisible();
                    play.click(play.getByText("main.js"));
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.expect(placeholder).not().toBeVisible();
                    // 'Add File or Folder' opens a menu (New Folder, New File, Upload File);
                    // 'New File' opens the name dialog
                    // It is a menu button, which says whether its menu is open
                    final Query add = play.getByRole("button", "Add File or Folder");
                    play.expect(add).toHaveAttribute("aria-haspopup", "menu");
                    play.expect(add).toHaveAttribute("aria-expanded", "false");
                    play.click(add);
                    // The menu's items, by their text
                    final Value<List<String>> items = screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT).textContents();
                    play.waitFor(() -> play.expect("the menu's items", items)
                            .toEqual(List.of("New Folder", "New File", "Upload File")));
                    play.waitFor(() -> play.expect(add).toHaveAttribute("aria-expanded", "true"));
                    play.click(screen.getByText("New File", StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(add).toHaveAttribute("aria-expanded", "false"));
                    play.waitFor(() -> play.expect(screen.querySelector(ASSET_NAME)).not().toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                // A new file's name with a character a name can't hold is refused, saying which
                // (the dialog said "Item name not set" for any name it refused)
                .story("NewFileIllegalName", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Assets")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Assets"));
                    play.waitFor(() -> play.expect(play.getByText("style.css")).toBeInTheDocument());
                    play.click(play.getByRole("button", "Add File or Folder"));
                    play.click(screen.findByText("New File", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = screen.within(screen.findByText("New File", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.type(dialog.querySelector(ASSET_NAME), "a/b.css");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            // The spy records the message as HTML, its quotes escaped
                            ValueMatcher.stringContaining("File names must not contain the character")));
                    play.expect(screen.getByText("New File", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                })
                // New File: OK with a name adds it; Cancel adds nothing
                .story("NewFile", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Assets")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Assets"));
                    play.waitFor(() -> play.expect(play.getByText("style.css")).toBeInTheDocument());
                    play.click(play.getByRole("button", "Add File or Folder"));
                    play.click(screen.findByText("New File", StroomDom.MENU_ITEM_TEXT));
                    final Play cancelled = screen.within(screen.findByText("New File", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.type(cancelled.querySelector(ASSET_NAME), "unwanted.css");
                    play.click(cancelled.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("New File", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.click(play.getByRole("button", "Add File or Folder"));
                    play.click(screen.findByText("New File", StroomDom.MENU_ITEM_TEXT));
                    final Play dialog = screen.within(screen.findByText("New File", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.waitFor(() -> play.expect(dialog.querySelector(ASSET_NAME)).toHaveFocus());
                    play.type(dialog.querySelector(ASSET_NAME), "extra.css");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(NEW_FILE).withBodyContaining("extra.css").toSpyMatcher()));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.put(NEW_FILE).withBodyContaining("unwanted.css").toSpyMatcher());
                    play.waitFor(() -> play.expect(screen.queryByText("New File", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                // Rename Selected Asset starts with its name; OK renames it
                .story("RenameAsset", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Assets")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Assets"));
                    play.click(play.findByText("style.css"));
                    play.click(play.getByRole("button", "Rename Selected Asset"));
                    final Play dialog = screen.within(screen.findByText("Edit", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    final Query name = dialog.getByLabelText("Name");
                    play.expect(name).toHaveValue("style.css");
                    play.clear(name);
                    play.type(name, "main.css");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RENAME).withBodyContaining("main.css").toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit", StroomDom.DIALOG_TITLE)).toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(FUNCTION_NAME)).toHaveAttribute("readonly"));
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).toHaveAttribute("readonly"));
                    play.expect(play.getByRole("button", "Save (read only)"))
                            .toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // The Assets tab, to compare with its read only partner
                .story("AssetsEditable", context -> render(context, false))
                .withPlay(play -> {
                    openTab(play, "Assets");
                    play.waitFor(() -> play.expect(play.getByText("style.css")).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // Read only: the Assets tab, as 'AssetsEditable' shows it
                .story("AssetsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    openTab(play, "Assets");
                    play.waitFor(() -> play.expect(play.getByText("style.css")).toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
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
                    play.waitFor(() -> play.expect(play.getByRole("button", "Revert Asset Changes"))
                            .toBeInTheDocument());
                    play.expect(play.queryByRole("button", "Save assets to live")).toBeNull();
                    play.expect(play.queryByRole("button", "Revert assets to live")).toBeNull();

                    // Publishing is the post save callback, so the play saves the document (after an
                    // edit) and checks the request, twice: the first publish succeeds, the second
                    // replies false
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

    // Opens one of the editor's sub-tabs
    private static void openTab(final Play play, final String label) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
        play.click(DocEditors.tab(play, label));
    }

    // Edits the function name and saves the document
    private static void saveAfterEdit(final Play play, final String text) {
        play.type(play.findByDisplayValue(text.equals("Chart")
                ? "D3.Bar"
                : "D3.BarChart"), text);
        play.tab();
        final Query save = play.getByRole("button", "Save");
        play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
        play.click(save);
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final RestFixtures fixtures = DocEditors.docSelectionRoutes(
                        DocEditors.permissionRoutes(RestFixtures.builder()))
                .get("/visualisation/v1/vis-1", RestReply.json(DOC))
                .put("/visualisation/v1/vis-1", request -> RestReply.json(request.getBody()))
                .get("/visualisationAssets/fetchDraftAssets/vis-1", RestReply.json(ASSETS))
                .put(NEW_FILE, RestReply.json("true"))
                .put(RENAME, RestReply.json("true"))
                .get("/visualisationAssets/getContent/vis-1", RestReply.json(
                        "{\"content\": \"// src/main.js\\nconsole.log(1);\", \"editorMode\": \"JAVASCRIPT\"}"))
                // Publishing: true, then false for the second save
                .put(SAVE_TO_LIVE, RestReply.json("true"), RestReply.json("false"))
                .build();
        final VisualisationResource resource = GWT.create(VisualisationResource.class);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getVisualisationEditor(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
