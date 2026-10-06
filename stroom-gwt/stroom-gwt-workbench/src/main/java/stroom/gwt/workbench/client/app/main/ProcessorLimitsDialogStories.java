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

import stroom.dashboard.client.query.ProcessorLimitsPresenter;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/ProcessorLimitsDialog` in the React Storybook, showing Stroom's real
/// [ProcessorLimitsPresenter] (the 'Process Search Results' dialog), shown as
/// `QueryPresenter.setProcessorLimits` shows it, with the UI config's process limits.
///
/// In Stroom the pipeline is chosen first, in its own 'Choose Pipeline To Process Results With'
/// popup, so this dialog only has the limits. React's `create` (which `QueryPresenter` calls after
/// OK, as `POST /processorFilter/v1`) isn't reached by the story.
public final class ProcessorLimitsDialogStories {

    private ProcessorLimitsDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ProcessorLimitsDialog", ProcessorLimitsDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The dialog renders the limits
                .story("Render", ProcessorLimitsDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Process Search Results")).toBeInTheDocument());
                    // Differs from React: GWT's dialog has no pipeline picker (the pipeline is chosen
                    // before it, in a 'Choose Pipeline To Process Results With' popup), so OK is
                    // enabled; the dialog holds the record and time limits, from the UI config
                    play.expect(screen.queryByText("Pipeline", "label")).toBeNull();
                    play.expect(screen.getByText("Record Limit", "label")).toBeInTheDocument();
                    play.expect(screen.querySelector("#processorLimitsRecordLimit input")).toHaveValue("1000000");
                    play.expect(screen.querySelector("#processorLimitsTimeLimit input")).toHaveValue("30");
                    play.expect(screen.getByRole("button", StroomDom.button("OK"))).toBeEnabled();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.none())
                .injector(injector)
                .build();
        final ProcessorLimitsPresenter presenter = injector.getProcessorLimitsPresenter();
        // As QueryPresenter.setProcessorLimits shows it
        harness.afterStartUp(() -> harness.getUiConfigCache().get(result -> {
            if (result != null) {
                presenter.setTimeLimitMins(result.getProcess().getDefaultTimeLimit());
                presenter.setRecordLimit(result.getProcess().getDefaultRecordLimit());
                ShowPopupEvent.builder(presenter)
                        .popupType(PopupType.OK_CANCEL_DIALOG)
                        .caption("Process Search Results")
                        .onShow(e -> presenter.getView().focus())
                        .onHideRequest(e -> e.hide())
                        .fire();
            }
        }, presenter));
        return harness.asWidget();
    }
}
