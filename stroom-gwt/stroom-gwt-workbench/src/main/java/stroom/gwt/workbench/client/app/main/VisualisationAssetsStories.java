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

package stroom.gwt.workbench.client.app.main;

import stroom.docref.DocRef;
import stroom.document.asset.client.component.DocumentAssetSelectionController;
import stroom.document.asset.client.component.DocumentAssetsTabController;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/VisualisationAssets`, showing Stroom's real [DocumentAssetsTabController]
/// (a Visualisation's 'Assets' tab: the asset tree with its toolbar) and its 'Add File' upload
/// dialog with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `GET /visualisationAssets/fetchDraftAssets/{uuid}` (a sequence, see below) | the draft assets |
/// | the file's upload (`importfile.rpc`, the upload spy) | uploading a file |
/// | `PUT /visualisationAssets/updateNewUploadedFile/{uuid}` | creating the uploaded file |
///
/// The draft assets are the seed (`chart.js`), then the seed and the uploaded file. Each file name
/// has its own upload reply, with the key `rk-<name>`.
///
/// The controller (from the editors batch's ginjector) is read as `VisualisationPresenter` reads
/// its tabs, and shown with its toolbar above it, as the document's tab shows them, filling the
/// page.
///
/// The tree stories have folders (`GET /visualisationAssets/getContent/{uuid}` is a file's
/// content): the tab's tree with its keys, and the 'Choose Asset' dialog that a floor map's asset
/// picker opens, chosen from by the keys.
public final class VisualisationAssetsStories {

    private static final String OWNER_UUID = "vis-1";
    private static final String CREATE_PATH = "/visualisationAssets/updateNewUploadedFile/" + OWNER_UUID;
    private static final String UPLOAD_URL = "importfile.rpc";
    private static final String CONTENT_PATH = "/visualisationAssets/getContent/" + OWNER_UUID;

    // The seed, then the assets with the uploaded file
    private static final String ASSETS = """
            {"ownerId": "vis-1", "dirty": false, "assets": [
                {"id": "a1", "path": "chart.js", "folder": false}MORE],
              "uploadedFiles": {}}""";

    // Folders and files, out of order: the tree shows folders first, each sorted by name
    private static final String TREE_ASSETS = """
            {"ownerId": "vis-1", "dirty": false, "assets": [
                {"id": "a1", "path": "js/chart.js", "folder": false},
                {"id": "a2", "path": "index.html", "folder": false},
                {"id": "a3", "path": "js/lib/d3.js", "folder": false},
                {"id": "a4", "path": "css/style.css", "folder": false}],
              "uploadedFiles": {}}""";

    private VisualisationAssetsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/VisualisationAssets", VisualisationAssetsStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Upload File: the chosen file is uploaded, then added to the assets with the
                // upload's resource key
                .story("UploadFile", context -> render(context, "my-vis.js"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = openUploadDialog(play);
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "my-vis.js", "(function(){})",
                            "text/javascript");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // The file is uploaded
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.UPLOAD_SPY))
                            .toHaveBeenCalledWith(UPLOAD_URL, "my-vis.js", "(function(){})"));
                    // The file is created with its path and the upload's resource key
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(CREATE_PATH)
                                    .withJsonBodyContaining(
                                            "{\"path\": \"my-vis.js\", \"resourceKey\": {\"key\": \"rk-my-vis.js\"}}")
                                    .toSpyMatcher()));
                    // GWT closes the dialog and fetches the assets again, so the tree shows the file
                    play.waitFor(() -> play.expect(screen.queryByText("Add File", StroomDom.DIALOG_TITLE)).toBeNull());
                    play.waitFor(() -> play.expect(play.getByText("my-vis.js")).toBeVisible());
                    ContentStorySupport.expectNoProblems(play);
                })
                // Uploading a name that clashes with an existing file gets a '-1' suffix
                .story("UploadDeduplicatesName", context -> render(context, "chart-1.js"))
                .withPlay(play -> {
                    final Play dialog = openUploadDialog(play);
                    // chart.js already exists at the root (the seed), so the upload becomes chart-1.js
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "chart.js", "x", "text/javascript");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(CREATE_PATH)
                                    .withJsonBodyContaining(
                                            "{\"path\": \"chart-1.js\", \"resourceKey\": {\"key\": \"rk-chart.js\"}}")
                                    .toSpyMatcher()));
                    play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).toHaveBeenCalledWith(UPLOAD_URL, "chart.js", "x");
                    play.waitFor(() -> play.expect(play.getByText("chart-1.js")).toBeVisible());
                    ContentStorySupport.expectNoProblems(play);
                })
                // The tree's keys: the choice follows the focus, so moving to a file shows it in the
                // editor; Right and Left open and close a folder, or go to its first child and its
                // parent; Enter opens or closes a folder; typing goes to an item by its name
                .story("TreeKeys", VisualisationAssetsStories::renderTree)
                .withPlay(play -> {
                    play.expect(play.findByRole("tree", "Assets")).toBeInTheDocument();
                    play.click(play.findByText("css"));
                    play.expect(item(play, "css")).toHaveAttribute("aria-selected", "true");
                    play.expect(item(play, "css")).toHaveAttribute("aria-level", "1");
                    play.expect(item(play, "css")).toHaveAttribute("aria-posinset", "1");
                    play.expect(item(play, "css")).toHaveAttribute("aria-setsize", "3");
                    play.expect(item(play, "css")).toHaveAttribute("aria-expanded", "false");
                    play.keyboard("{ArrowDown}");
                    play.expect(item(play, "js")).toHaveFocus();
                    play.expect(item(play, "js")).toHaveAttribute("aria-selected", "true");
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(item(play, "js")).toHaveAttribute("aria-expanded", "true"));
                    // The folder's first child is its folder
                    play.keyboard("{ArrowRight}");
                    play.expect(item(play, "lib")).toHaveFocus();
                    play.expect(item(play, "lib")).toHaveAttribute("aria-level", "2");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(item(play, "lib")).toHaveAttribute("aria-expanded", "true"));
                    play.keyboard("{ArrowDown}");
                    play.expect(item(play, "d3.js")).toHaveAttribute("aria-level", "3");
                    play.keyboard("{ArrowDown}");
                    // The file is shown in the editor
                    play.expect(item(play, "chart.js")).toHaveAttribute("aria-selected", "true");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get(CONTENT_PATH).withQuery("path=%2Fjs%2Fchart.js").toSpyMatcher()));
                    play.keyboard("{ArrowLeft}");
                    play.expect(item(play, "js")).toHaveFocus();
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(item(play, "js")).toHaveAttribute("aria-expanded", "false"));
                    play.expect(play.queryByText("chart.js")).toBeNull();
                    play.keyboard("i");
                    play.expect(item(play, "index.html")).toHaveFocus();
                    play.expect(item(play, "index.html")).toHaveAttribute("aria-selected", "true");
                    ContentStorySupport.expectNoProblems(play);
                })
                // A floor map's asset picker opens 'Choose Asset': only a file can be chosen, and Enter
                // on one chooses it, as OK does
                .story("ChooseAssetByKeyboard", VisualisationAssetsStories::renderChooser)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("None"));
                    final Play dialog = screen.within(
                            screen.findByText("Choose Asset", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(dialog.findByText("js"));
                    play.expect(dialog.getByRole("button", StroomDom.button("OK"))).toBeDisabled();
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(item(dialog, "js")).toHaveAttribute("aria-expanded", "true"));
                    play.keyboard("{ArrowDown}");
                    play.expect(item(dialog, "lib")).toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    play.expect(item(dialog, "chart.js")).toHaveAttribute("aria-selected", "true");
                    play.expect(dialog.getByRole("button", StroomDom.button("OK"))).toBeEnabled();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.queryByText("Choose Asset", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.getByText("/assets/vis-1/js/chart.js")).toBeVisible();
                    ContentStorySupport.expectNoProblems(play);
                })
                // OK is disabled until a file is chosen; a file chosen and OK pressed is the picker's
                // value, and a double click on another chooses that one at once
                .story("ChooseAssetWithTheMouse", VisualisationAssetsStories::renderChooser)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("None"));
                    final Play dialog = screen.within(
                            screen.findByText("Choose Asset", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.expect(dialog.findByText("index.html")).toBeVisible();
                    play.expect(dialog.getByRole("button", StroomDom.button("OK"))).toBeDisabled();
                    play.click(dialog.getByText("index.html"));
                    play.expect(dialog.getByRole("button", StroomDom.button("OK"))).toBeEnabled();
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.expect(play.findByText("/assets/vis-1/index.html")).toBeVisible();

                    play.click(play.getByText("/assets/vis-1/index.html"));
                    final Play again = screen.within(
                            screen.findByText("Choose Asset", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(again.findByText("css"));
                    play.keyboard("{ArrowRight}");
                    play.dblClick(again.findByText("style.css"));
                    play.expect(play.findByText("/assets/vis-1/css/style.css")).toBeVisible();
                    ContentStorySupport.expectNoProblems(play);
                })
                // Cancel leaves the picker's value as it was, even with a file chosen
                .story("CancelKeepsTheAsset", VisualisationAssetsStories::renderChooser)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("None"));
                    final Play dialog = screen.within(
                            screen.findByText("Choose Asset", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(dialog.findByText("index.html"));
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Choose Asset", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.getByText("None")).toBeVisible();
                    ContentStorySupport.expectNoProblems(play);
                })
                // A disabled picker says so, and doesn't open
                .story("DisabledPicker", context -> renderChooser(context, false))
                .withPlay(play -> {
                    final Query picker = play.findByRole("button", TextMatch.containing("None"));
                    play.expect(picker).toHaveAttribute("aria-disabled", "true");
                    play.click(picker);
                    play.expect(play.screen().queryByText("Choose Asset", StroomDom.DIALOG_TITLE)).toBeNull();
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    // An item of the tree, by its name
    private static Query item(final Play play, final String name) {
        return play.getByText(name).closest("[role='treeitem']");
    }

    /// 'Add File or Folder' then 'Upload File' opens the upload dialog.
    ///
    /// @return The dialog.
    private static Play openUploadDialog(final Play play) {
        final Play screen = play.screen();
        // GWT reaches Upload File through the toolbar's 'Add File or Folder' button's menu
        play.click(play.findByTitle("Add File or Folder"));
        play.click(screen.findByText("Upload File", StroomDom.MENU_ITEM_TEXT));
        // The dialog's caption is 'Add File', and Stroom's dialogs have no role="dialog"
        return screen.within(screen.findByText("Add File", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // The tab's tree with folders, and the content of each file chosen
    private static Widget renderTree(final StoryContext context) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/visualisationAssets/fetchDraftAssets/" + OWNER_UUID, RestReply.json(TREE_ASSETS))
                .get(CONTENT_PATH, RestReply.json("{\"content\": \"// A file\", \"editorMode\": \"JAVASCRIPT\"}"))
                .build();
        return render(context, fixtures);
    }

    // A floor map's asset picker, for the document with folders
    private static Widget renderChooser(final StoryContext context) {
        return renderChooser(context, true);
    }

    // A floor map's asset picker, for the document with folders, enabled or not
    private static Widget renderChooser(final StoryContext context, final boolean enabled) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/visualisationAssets/fetchDraftAssets/" + OWNER_UUID, RestReply.json(TREE_ASSETS))
                .build();
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        final SimplePanel frame = new SimplePanel();
        frame.getElement().getStyle().setProperty("width", "300px");
        harness.add(frame);
        harness.afterStartUp(() -> {
            final DropDownBox box = new DropDownBox();
            final DocumentAssetSelectionController picker = harness.own(
                    injector.getDocumentAssetSelectionController()).attach(box);
            picker.setDocument(VisualisationDoc.builder().uuid(OWNER_UUID).name("My Vis").build());
            picker.setEnabled(enabled);
            frame.setWidget(box);
        });
        return harness.asWidget();
    }

    private static Widget render(final StoryContext context, final String createdPath) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/visualisationAssets/fetchDraftAssets/" + OWNER_UUID,
                        RestReply.json(ASSETS.replace("MORE", "")),
                        RestReply.json(ASSETS.replace("MORE",
                                ", {\"id\": \"a2\", \"path\": \"" + createdPath + "\", \"folder\": false}")))
                // Each upload's resource key is named after the file
                .upload("my-vis.js", UploadReply.success("rk-my-vis.js", "my-vis.js"))
                .upload("chart.js", UploadReply.success("rk-chart.js", "chart.js"))
                // Creating the uploaded file
                .put(CREATE_PATH, RestReply.json("true"))
                .build();
        return render(context, fixtures);
    }

    // The tab, with its toolbar above it, reading the fake document
    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, OWNER_UUID, "My Vis");
        final VisualisationDoc doc = VisualisationDoc.builder().uuid(OWNER_UUID).name("My Vis").build();

        // The toolbar above the tab's content, as the document's tab shows them, filling the page
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", StroomThemeDecorator.CANVAS_HEIGHT);
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        harness.add(frame);
        // After start-up, as its editor reads the user's preferences
        harness.afterStartUp(() -> {
            // As its document's opener says, which its tabs follow
            harness.getComponentRoot().provide(Contexts.READ_ONLY, ContextValue.of(false));
            final DocumentAssetsTabController<VisualisationDoc> assetsTab = harness.own(
                    injector.getVisualisationAssetsTab());
            for (final Widget toolbar : assetsTab.getDocumentButtons()) {
                toolbar.getElement().getStyle().setProperty("flex", "0 0 auto");
                frame.add(toolbar);
            }
            final Widget content = assetsTab.asWidget();
            content.getElement().getStyle().setProperty("flex", "1 1 auto");
            content.getElement().getStyle().setProperty("minHeight", "0");
            content.getElement().getStyle().setProperty("position", "relative");
            frame.add(content);
            // As VisualisationPresenter reads its tabs
            assetsTab.read(docRef, doc);
        });
        return harness.asWidget();
    }
}
