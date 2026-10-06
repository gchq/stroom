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

import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.security.shared.DocumentPermission;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/BeginSteppingDialog` in the React Storybook, showing what Stroom
/// shows for a `BeginPipelineSteppingEvent` with no pipeline: the real [DocSelectionPopup]
/// captioned 'Choose Pipeline To Step With', with the pipeline the server guesses for the stream
/// selected, as `PipelinePlugin.onBeginStepping` shows it (the story does the same as the plugin,
/// which needs the whole app to be created).
///
/// | React | Stroom |
/// |---|---|
/// | `getPipelineForStepping` | `POST /stepping/v1/getPipelineForStepping` |
/// | (the chooser) | the explorer tree (`POST /explorer/v2/fetchExplorerNodes`, `getFromDocRef`) |
/// | `fetchPipelineDoc` | `POST /meta/v1/find` (`PipelinePlugin.step` finds the stream before opening the pipeline) |
///
/// React's `onBegin` is a spy on what `PipelinePlugin` would then open stepping with.
public final class BeginSteppingDialogStories {

    /// The name of the spy recording the stepping begun (React's `onBegin`).
    static final String ON_BEGIN = "onBegin";

    private static final String PIPELINE_UUID = "p1";
    private static final String CAPTION = "Choose Pipeline To Step With";

    private static final String META_ROWS = """
            {
              "values": [
                {"meta": {"id": 42, "feedName": "TEST_FEED", "typeName": "Raw Events", "status": "UNLOCKED",
                  "createMs": 1700000000000}}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = TreeFixtures.explorerRoutes(new ExplorerFixture(
                    ExplorerFixture.folder("System",
                            ExplorerFixture.doc("Guessed Pipeline", PipelineDoc.TYPE).withUuid(PIPELINE_UUID))))
            .post("/stepping/v1/getPipelineForStepping", RestReply.json(
                    "{\"type\": \"Pipeline\", \"uuid\": \"" + PIPELINE_UUID + "\", \"name\": \"Guessed Pipeline\"}"))
            .post("/meta/v1/find", RestReply.json(META_ROWS))
            .build();

    private BeginSteppingDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/BeginSteppingDialog", BeginSteppingDialogStories.class)
                // Guessing pre-selects a pipeline; OK begins stepping the stream with it
                .story("GuessThenBegin", BeginSteppingDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = screen.within(
                            screen.findByText(CAPTION, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    // The guessed pipeline is selected in the chooser
                    dialog.findByText("Guessed Pipeline");
                    final Query ok = dialog.getByRole("button", StroomDom.button("OK"));
                    play.waitFor(() -> play.expect(ok).toBeEnabled());
                    play.click(ok);
                    // The stream is found by its id (FindMetaCriteria.createFromId), then stepping begins
                    play.waitFor(() -> play.expect(play.spy(ON_BEGIN)).toHaveBeenCalledTimes(1));
                    play.expect(play.spy(ON_BEGIN)).toHaveBeenCalledWith(
                            "pipeline=" + PIPELINE_UUID + ", childStreamType=RAW, metaId=42");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/meta/v1/find").withBodyContaining("42").toSpyMatcher());
                    // Differs from React: there is no onClose; the chooser closes itself on OK
                    play.waitFor(() -> play.expect(screen.queryByText(CAPTION, StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.fn(ON_BEGIN);
        final SteppingResource steppingResource = GWT.create(SteppingResource.class);
        final MetaResource metaResource = GWT.create(MetaResource.class);
        final RestFactory restFactory = harness.getRestFactory();
        final StepLocation stepLocation = StepLocation.first(42);
        final String childStreamType = "RAW";

        // As PipelinePlugin.onBeginStepping does for an event with no pipeline
        final DocSelectionPopup chooser = injector.getDocSelectionPopup();
        chooser.setCaption(CAPTION);
        chooser.setIncludedTypes(PipelineDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.VIEW);
        final Runnable showChooser = () -> chooser.show(pipeline -> {
            if (pipeline != null) {
                // PipelinePlugin.step
                restFactory
                        .create(metaResource)
                        .method(res -> res.findMetaRow(FindMetaCriteria.createFromId(stepLocation.getMetaId())))
                        .onSuccess(result -> {
                            if (result != null && result.size() == 1) {
                                final MetaRow row = result.getFirst();
                                harness.spy(ON_BEGIN, "pipeline=" + pipeline.getUuid()
                                                      + ", childStreamType=" + childStreamType
                                                      + ", metaId=" + row.getMeta().getId());
                            }
                        })
                        .taskMonitorFactory(chooser)
                        .exec();
            }
        }, PopupType.CREATE_OK_CANCEL_DIALOG);
        harness.afterStartUp(() -> restFactory
                .create(steppingResource)
                .method(res -> res.getPipelineForStepping(new GetPipelineForMetaRequest(
                        stepLocation.getMetaId(), null)))
                .onSuccess(docRef -> chooser.setSelectedEntityReference(docRef, showChooser))
                .taskMonitorFactory(chooser)
                .exec());
        return harness.asWidget();
    }
}
