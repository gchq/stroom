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
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.component.StepControl;
import stroom.pipeline.stepping.client.component.StepControlController;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.user.client.ui.Widget;

import java.util.Locale;
import java.util.function.Consumer;

/// Stories for Stroom's stepping controls, [StepControlController] and [StepControl], the real
/// controller and component, owned by the story's harness. Each story sets the component's
/// per-button enabled state (`StepControl.setStepEnabled`), and the `onStep`/`onFilter` spies report
/// the steps and filter requests the controller gives its callbacks.
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
                    final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
                    final InlineLabel last = StoryPanels.note("—", "#dce4e5", "12px");
                    final StepControl view = new StepControl();
                    stepControls(harness, view, false, last::setText);
                    enableAll(view);
                    final Widget column = StoryPanels.column(8, view, last);
                    column.getElement().getStyle().setProperty("alignItems", "center");
                    harness.add(column);
                    return harness.asWidget();
                })
                // Initial state - every step button disabled until a stream is chosen (GWT initButtons)
                .story("Initial", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
                    final StepControlController controller = stepControls(harness, new StepControl(), false,
                            report -> {
                            });
                    // Refresh is disabled too, as there is nothing to step until a stream is chosen
                    // (gwt-bugs #36)
                    controller.initButtons();
                    harness.add(controller.asWidget());
                    return harness.asWidget();
                })
                // Busy - every button suppresses clicks while a step is in flight
                .story("Busy", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
                    final StepControl view = new StepControl();
                    stepControls(harness, view, true, report -> {
                    });
                    // The step controls have no busy state. While a step is in flight Stroom's
                    // SteppingScreenController ignores step events (busyTranslating), but the buttons stay
                    // enabled, and the filter button still works.
                    enableAll(view);
                    harness.add(view);
                    return harness.asWidget();
                });
    }

    /// Creates the real controller for the buttons, owned by the harness, reporting the steps to the
    /// `onStep`/`onFilter` spies.
    ///
    /// @param busy   True to ignore steps (not the filter), as `SteppingScreenController` does while it is
    ///               busy translating.
    /// @param report Also told each step ("step: forward") and filter request ("filter").
    private static StepControlController stepControls(final ScreenHarness harness,
                                                      final StepControl view,
                                                      final boolean busy,
                                                     final Consumer<String> report) {
        final StepControlController controller = harness.own(new StepControlController(harness.getEventBus(), view));
        final Spy onStep = harness.fn(ON_STEP);
        final Spy onFilter = harness.fn(ON_FILTER);
        controller.setOnStep(stepType -> {
            report.accept("step: " + stepType.name().toLowerCase(Locale.ROOT));
            if (!busy) {
                onStep.call(stepType.name().toLowerCase(Locale.ROOT));
            }
        });
        // Not guarded: SteppingScreenController opens the filter dialog even while it is busy
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
