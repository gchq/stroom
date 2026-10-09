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

import stroom.data.client.presenter.ProcessChoice;
import stroom.data.client.presenter.ProcessChoicePresenter;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.Selection;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.Map;

/// The `App/Data/ProcessChoiceDialog` stories, showing Stroom's real [ProcessChoicePresenter] (the
/// 'Create Processors' dialog), as the data browser's meta list (`AbstractMetaListPresenter`) shows
/// it for a selection of streams.
///
/// A spy records the dialog's [ProcessChoice]. In Stroom the pipeline is chosen after the dialog's
/// OK (for a process, not a reprocess), in a 'Choose Pipeline To Process Data With' popup, so the
/// dialog itself has no pipeline picker and needs no explorer tree.
public final class ProcessChoiceDialogStories {

    /// The name of the spy recording the choice made.
    static final String ON_OK = "onOk";

    private ProcessChoiceDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Data/ProcessChoiceDialog", ProcessChoiceDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A non-reprocess run needs a pipeline
                .story("ProcessRequiresAPipeline", ProcessChoiceDialogStories::render)
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
                .story("ReprocessData", ProcessChoiceDialogStories::render)
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
                // Turning "Enable new filters" off is reflected in the choice
                .story("DisableEnabled", ProcessChoiceDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Create Processors")).toBeInTheDocument());
                    play.click(screen.getAllByRole("checkbox").nth(1));
                    play.click(screen.getAllByRole("checkbox").nth(2));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_OK)).toHaveBeenCalledWith(
                            matchesChoice(10, true, true, false)));
                    expectNoProblems(play);
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
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.none())
                .injector(injector)
                .build();
        final Spy onOk = harness.fn(ON_OK);
        final ProcessChoicePresenter presenter = injector.getProcessChoicePresenter();
        // A selection of all the streams, as the meta list's 'Process' item gives it
        final Selection<Long> selection = Selection.selectAll();
        presenter.show(selection, choice -> {
            final Map<String, Object> map = choice(
                    choice.getPriority(), choice.isAutoPriority(), choice.isReprocess(), choice.isEnabled());
            map.put("minMetaCreateTimeMs", choice.getMinMetaCreateTimeMs());
            map.put("maxMetaCreateTimeMs", choice.getMaxMetaCreateTimeMs());
            onOk.call(map);
        });
        return harness.asWidget();
    }
}
