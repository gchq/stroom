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
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.visualisation.client.presenter.VisualisationAssetsPresenter;
import stroom.visualisation.shared.VisualisationDoc;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/VisualisationAssets` in the React Storybook, showing Stroom's real
/// [VisualisationAssetsPresenter] (a Visualisation's 'Assets' tab: the asset tree with its
/// toolbar) and its 'Add File' upload dialog with fake REST replies.
///
/// | React seam | Stroom |
/// |---|---|
/// | `api.fetchDraft` | `GET /visualisationAssets/fetchDraftAssets/{uuid}` (a sequence, see below) |
/// | `upload` (its `uploaded` recorder) | the file's upload (`importfile.rpc`, the upload spy) |
/// | `api.createFile` (its `created` recorder) | `PUT /visualisationAssets/updateNewUploadedFile/{uuid}` |
///
/// The draft assets are the seed (`chart.js`), then the seed and the uploaded file, as React's
/// store grows. Each file name has its own upload reply, with the key `rk-<name>`.
///
/// The presenter (from the editors batch's ginjector) is read as `VisualisationPresenter` reads
/// its tabs, and shown with its toolbar above it, as the document's tab shows them, filling the
/// page (React's `100vh` box).
public final class VisualisationAssetsStories {

    private static final String OWNER_UUID = "vis-1";
    private static final String CREATE_PATH = "/visualisationAssets/updateNewUploadedFile/" + OWNER_UUID;
    private static final String UPLOAD_URL = "importfile.rpc";

    // React's SEED, then the store with the uploaded file
    private static final String ASSETS = """
            {"ownerId": "vis-1", "dirty": false, "assets": [
                {"id": "a1", "path": "chart.js", "folder": false}MORE],
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
                    // React's uploaded recorder
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.UPLOAD_SPY))
                            .toHaveBeenCalledWith(UPLOAD_URL, "my-vis.js", "(function(){})"));
                    // React's created recorder: the path and the upload's resource key
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
                    // chart.js already exists at the root (React's SEED), so the upload becomes
                    // chart-1.js
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
                });
    }

    /// 'Add file' then 'Upload File' opens the upload dialog.
    ///
    /// @return The dialog.
    private static Play openUploadDialog(final Play play) {
        final Play screen = play.screen();
        // GWT reaches Upload File through the toolbar's 'Add file' button's menu
        play.click(play.findByTitle("Add file"));
        play.click(screen.findByText("Upload File", StroomDom.MENU_ITEM_TEXT));
        // Differs from React: the dialog's caption is 'Add File' (React: 'Upload File'), and
        // Stroom's dialogs have no role="dialog"
        return screen.within(screen.findByText("Add File", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    private static Widget render(final StoryContext context, final String createdPath) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/visualisationAssets/fetchDraftAssets/" + OWNER_UUID,
                        RestReply.json(ASSETS.replace("MORE", "")),
                        RestReply.json(ASSETS.replace("MORE",
                                ", {\"id\": \"a2\", \"path\": \"" + createdPath + "\", \"folder\": false}")))
                // React's upload: the resource key is named after the file
                .upload("my-vis.js", UploadReply.success("rk-my-vis.js", "my-vis.js"))
                .upload("chart.js", UploadReply.success("rk-chart.js", "chart.js"))
                // React's createFile
                .put(CREATE_PATH, RestReply.json("true"))
                .build();
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, OWNER_UUID, "My Vis");
        final VisualisationDoc doc = VisualisationDoc.builder().uuid(OWNER_UUID).name("My Vis").build();

        // The toolbar above the tab's content, as the document's tab shows them, in React's
        // 100vh box
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", "100vh");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        harness.add(frame);
        // After start-up, as its editor reads the user's preferences
        harness.afterStartUp(() -> {
            final VisualisationAssetsPresenter presenter = harness.unbindOnCleanUp(
                    injector.getVisualisationAssetsPresenter());
            for (final Widget toolbar : presenter.getToolbars()) {
                toolbar.getElement().getStyle().setProperty("flex", "0 0 auto");
                frame.add(toolbar);
            }
            final Widget content = presenter.getWidget();
            content.getElement().getStyle().setProperty("flex", "1 1 auto");
            content.getElement().getStyle().setProperty("minHeight", "0");
            content.getElement().getStyle().setProperty("position", "relative");
            frame.add(content);
            // As VisualisationPresenter reads its tabs
            presenter.read(docRef, doc, false);
        });
        return harness.asWidget();
    }
}
