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
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.component.StepControl;
import stroom.pipeline.stepping.client.component.StepControlController;

import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.Locale;
import java.util.function.Consumer;

/// Stories for Stroom's stepping controls, [StepControlController] and [StepControl],
/// using the real presenter and view. Each story sets the view's per-button enabled state
/// (`StepControlView.setStepXxxEnabled`), and the `onStep`/`onFilter` spies report the presenter's
/// steps and filter requests it reports to its callbacks.
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
                    final StepControl view = new StepControl();
                    stepControls(context, view, false, last::setText);
                    enableAll(view);
                    final Widget column = StoryPanels.column(8, view, last);
                    column.getElement().getStyle().setProperty("alignItems", "center");
                    return column;
                })
                // Initial state - every step button disabled until a stream is chosen (GWT initButtons)
                .story("Initial", context -> {
                    final StepControlController controller = stepControls(context, new StepControl(), false,
                            report -> {
                            });
                    // Refresh is disabled too, as there is nothing to step until a stream is chosen
                    // (gwt-bugs #36)
                    controller.initButtons();
                    return controller.asWidget();
                })
                // Busy - every button suppresses clicks while a step is in flight
                .story("Busy", context -> {
                    final StepControl view = new StepControl();
                    stepControls(context, view, true, report -> {
                    });
                    // The step controls have no busy state. While a step is in flight Stroom's
                    // SteppingPresenter ignores step events (busyTranslating), but the buttons stay
                    // enabled, and the filter button still works.
                    enableAll(view);
                    return view;
                });
    }

    /// Creates the real controller for the buttons, reporting the steps to the `onStep`/`onFilter` spies.
    ///
    /// @param busy   True to ignore steps (not the filter), as `SteppingPresenter` does while it is
    ///               busy translating.
    /// @param report Also told each step ("step: forward") and filter request ("filter").
    private static StepControlController stepControls(final StoryContext context,
                                                      final StepControl view,
                                                      final boolean busy,
                                                     final Consumer<String> report) {
        final StepControlController controller = new StepControlController(new SimpleEventBus(), view);
        final Spy onStep = context.fn(ON_STEP);
        final Spy onFilter = context.fn(ON_FILTER);
        controller.setOnStep(stepType -> {
            report.accept("step: " + stepType.name().toLowerCase(Locale.ROOT));
            if (!busy) {
                onStep.call(stepType.name().toLowerCase(Locale.ROOT));
            }
        });
        // Not guarded: SteppingPresenter opens the filter dialog even while it is busy
        controller.setOnFilter(() -> {
            report.accept("filter");
            onFilter.call();
        });
        return controller;
    }

    /// Enables every step button.
    private static void enableAll(final StepControl view) {
        for (final StepType stepType : StepType.values()) {
            view.setStepEnabled(stepType, true);
        }
    }
}
