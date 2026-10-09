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

package stroom.gwt.workbench.client.widgets.buttons;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter.StepControlView;
import stroom.pipeline.stepping.client.view.StepControlViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.Locale;

/// Stories for Stroom's stepping controls, [StepControlPresenter] and [StepControlViewImpl],
/// matching `Widgets/Buttons/StepControls` in the React Storybook.
///
/// The real presenter and view are used. React's `states` prop is the view's per-button enabled
/// state (`StepControlView.setStepXxxEnabled`), and its `onStep`/`onFilter` callbacks are the
/// presenter's `StepControlEvent` and `ChangeFilterEvent`.
public final class StepControlsStories {

    private static final String ON_STEP = "onStep";
    private static final String ON_FILTER = "onFilter";

    private StepControlsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/StepControls", StepControlsStories.class)
                .layout(StoryLayout.CENTERED)
                // All navigation buttons enabled; the last click is reported below
                .story("AllEnabled", context -> {
                    final InlineLabel last = StoryPanels.note("—", "#dce4e5", "12px");
                    final StepControlPresenter presenter = stepControls(context, false);
                    enableAll(presenter.getView());
                    presenter.addStepControlHandler(event -> last.setText(
                            "step: " + event.getStepType().name().toLowerCase(Locale.ROOT)));
                    presenter.addChangeFilterHandler(event -> last.setText("filter"));
                    final Widget column = StoryPanels.column(8, presenter.getWidget(), last);
                    column.getElement().getStyle().setProperty("alignItems", "center");
                    return column;
                })
                // Initial state - every step button disabled until a stream is chosen (GWT initButtons)
                .story("Initial", context -> {
                    final StepControlPresenter presenter = stepControls(context, false);
                    // Differs from React: refresh is disabled too. React mirrors the old initButtons,
                    // whose enabled refresh sent a step request with no stream (gwt-bugs #36).
                    presenter.initButtons();
                    return presenter.getWidget();
                })
                // Busy - every button suppresses clicks while a step is in flight
                .story("Busy", context -> {
                    final StepControlPresenter presenter = stepControls(context, true);
                    // Differs from React: the GWT step controls have no busy state. While a step is
                    // in flight Stroom's SteppingPresenter ignores step events (busyTranslating),
                    // but the buttons stay enabled, so they don't get React's disabled class, and the
                    // filter button still works.
                    enableAll(presenter.getView());
                    return presenter.getWidget();
                });
    }

    /// Creates the real presenter and view, reporting the steps to the `onStep`/`onFilter` spies.
    ///
    /// @param busy True to ignore steps (not the filter), as `SteppingPresenter` does while it is
    ///             busy translating.
    private static StepControlPresenter stepControls(final StoryContext context, final boolean busy) {
        final StepControlViewImpl view = new StepControlViewImpl(GWT.create(StepControlViewImpl.Binder.class));
        final StepControlPresenter presenter = new StepControlPresenter(new SimpleEventBus(), view);
        final Spy onStep = context.fn(ON_STEP);
        final Spy onFilter = context.fn(ON_FILTER);
        presenter.addStepControlHandler(event -> {
            if (!busy) {
                onStep.call(event.getStepType().name().toLowerCase(Locale.ROOT));
            }
        });
        // Not guarded: SteppingPresenter opens the filter dialog even while it is busy
        presenter.addChangeFilterHandler(event -> onFilter.call());
        return presenter;
    }

    /// React's `ALL_ENABLED` states.
    private static void enableAll(final StepControlView view) {
        view.setStepFirstEnabled(true);
        view.setStepBackwardEnabled(true);
        view.setStepForwardEnabled(true);
        view.setStepLastEnabled(true);
        view.setStepRefreshEnabled(true);
    }
}
