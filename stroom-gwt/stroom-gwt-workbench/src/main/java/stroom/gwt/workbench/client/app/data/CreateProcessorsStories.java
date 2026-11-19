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

package stroom.gwt.workbench.client.app.data;

import stroom.data.client.ProcessChoice;
import stroom.data.client.component.CreateProcessorsController;
import stroom.explorer.client.component.DocumentChooserController;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Selection;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.Map;

/// The `App/Data/ProcessChoiceDialog` stories, showing Stroom's real [CreateProcessorsController] (the
/// 'Create Processors' dialog), as the data browser's meta list (`MetaListController`) shows
/// it for a selection of streams.
///
/// A spy records the dialog's [ProcessChoice]. In Stroom the pipeline is chosen after the dialog's
/// OK (for a process, not a reprocess), in a 'Choose Pipeline To Process Data With' popup, so the
/// dialog itself has no pipeline picker and needs no explorer tree.
public final class CreateProcessorsStories {

    /// The name of the spy recording the choice made.
    static final String ON_OK = "onOk";

    private static final String CHOOSER_CAPTION = "Choose Pipeline To Process Data With";

    private CreateProcessorsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Data/ProcessChoiceDialog", CreateProcessorsStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A non-reprocess run needs a pipeline
                .story("ProcessRequiresAPipeline", CreateProcessorsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Create Processors")).toBeInTheDocument());
                    // The dialog has no pipeline picker and OK is enabled; the meta list asks for
                    // the pipeline after OK when the choice isn't a reprocess
                    play.expect(screen.queryByText("Pipeline", "label")).toBeNull();
                    play.expect(screen.getByRole("button", StroomDom.button("OK"))).toBeEnabled();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_OK)).toHaveBeenCalledWith(
                            matchesChoice(10, true, false, true)));
                    expectNoProblems(play);
                })
                // Ticking "Reprocess data" makes a reprocess choice, with no pipeline
                .story("ReprocessData", CreateProcessorsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Create Processors")).toBeInTheDocument());
                    // Check boxes: [0] Auto Priority, [1] Reprocess data, [2] Enable new filters
                    play.click(screen.getAllByRole("checkbox").nth(1));
                    play.expect(screen.queryByText("Pipeline", "label")).toBeNull();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_OK)).toHaveBeenCalledWith(
                            matchesChoice(10, true, true, true)));
                    expectNoProblems(play);
                })
                // A create time that isn't a time is refused: OK says why under it and keeps the
                // dialog. Before, the text was taken as no time, so no lower limit
                .story("CreateTimeNotATimeRefused", CreateProcessorsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Create Processors")).toBeInTheDocument());
                    final Query minTime = screen.getByLabelText("Min Create Time");
                    play.type(minTime, "soon");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("Must be a date and time")).toBeInTheDocument());
                    play.expect(minTime).toHaveFocus();
                    play.expect(play.spy(ON_OK)).not().toHaveBeenCalled();
                    expectNoProblems(play);
                })
                // Turning "Enable new filters" off is reflected in the choice
                .story("DisableEnabled", CreateProcessorsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Create Processors")).toBeInTheDocument());
                    play.click(screen.getAllByRole("checkbox").nth(1));
                    play.click(screen.getAllByRole("checkbox").nth(2));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_OK)).toHaveBeenCalledWith(
                            matchesChoice(10, true, true, false)));
                    expectNoProblems(play);
                })
                // Choose Pipeline To Process Data With left open (the screenshot and the dialog
                // measurement show it): OK on a choice that isn't a reprocess asks for the pipeline
                .story("ChoosePipelineToProcessDataWithOpen", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play choice = screen.within(
                            screen.findByText("Create Processors", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(choice.getByRole("button", StroomDom.button("OK")));
                    final Play dialog = screen.within(
                            screen.findByText(CHOOSER_CAPTION, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    dialog.findByText("System");
                });
    }

    // Only the choice's members other than its create time range are compared, as Stroom sets the
    // max create time to now when Reprocess data is ticked
    private static ValueMatcher matchesChoice(final int priority,
                                              final boolean autoPriority,
                                              final boolean reprocess,
                                              final boolean enabled) {
        return ValueMatcher.objectContaining(choice(priority, autoPriority, reprocess, enabled));
    }

    private static Map<String, Object> choice(final int priority,
                                              final boolean autoPriority,
                                              final boolean reprocess,
                                              final boolean enabled) {
        final Map<String, Object> map = new HashMap<>();
        map.put("priority", priority);
        map.put("autoPriority", autoPriority);
        map.put("reprocess", reprocess);
        map.put("enabled", enabled);
        return map;
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context) {
        return render(context, false);
    }

    // As above; with choosePipeline, a choice that isn't a reprocess then shows the pipeline chooser,
    // as MetaListController.doProcess does
    private static Widget render(final StoryContext context, final boolean choosePipeline) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final RestFixtures fixtures = choosePipeline
                ? TreeFixtures.explorerRoutes(TreeFixtures.fixtureTree()).build()
                : RestFixtures.none();
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        final Spy onOk = harness.fn(ON_OK);
        // A selection of all the streams, as the meta list's 'Process' item gives it
        final Selection<Long> selection = Selection.selectAll();
        injector.getCreateProcessorsController().show(harness.getAppRoot(), selection, choice -> {
            final Map<String, Object> map = choice(
                    choice.getPriority(), choice.isAutoPriority(), choice.isReprocess(), choice.isEnabled());
            map.put("minMetaCreateTimeMs", choice.getMinMetaCreateTimeMs());
            map.put("maxMetaCreateTimeMs", choice.getMaxMetaCreateTimeMs());
            onOk.call(map);
            if (choosePipeline && !choice.isReprocess()) {
                // As MetaListController.choosePipeline
                final DocumentChooserController chooser = injector.getDocumentChooserController();
                chooser.setIncludeNullSelection(false);
                chooser.setCaption(CHOOSER_CAPTION);
                chooser.setIncludedTypes(PipelineDoc.TYPE);
                chooser.setRequiredPermissions(DocumentPermission.VIEW);
                chooser.showOnce(harness.getAppRoot(), pipeline -> {
                    // The story stops at the chooser
                });
            }
        });
        return harness.asWidget();
    }
}
