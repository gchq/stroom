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


package stroom.gwt.workbench.client.widgets.feedback;

import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.progress.client.presenter.Progress;
import stroom.widget.progress.client.presenter.ProgressPresenter;
import stroom.widget.progress.client.view.ProgressViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;

/// Stories for Stroom's [ProgressPresenter] (with [ProgressViewImpl]), the sliding window
/// progress bar of the source editor, matching `Widgets/Feedback/ProgressBar` in the React
/// Storybook.
public final class ProgressBarStories {

    private static final String ON_CLICK = "onClick";
    private static final double CLICKABLE_UPPER_BOUND = 500.0;

    private ProgressBarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's progress arg is only a default for its Controls; the stories render fixed progress
        registry.component("Widgets/Feedback/ProgressBar", ProgressBarStories.class)
                .layout(StoryLayout.PADDED)
                // A simple percentage progress bar at 65% (not clickable, so in the disabled colour)
                .story("Basic", context -> width400(progressBar(Progress.simplePercentage(65.0)).getWidget()))
                // A sliding window: showing 200-350 of a 0-1000 range
                .story("SlidingWindow", context -> width400(
                        progressBar(Progress.boundedRange(1000.0, 200.0, 350.0)).getWidget()))
                // Clickable: click to move the visible window; the clicked % is shown
                .story("Clickable", ProgressBarStories::clickable)
                // Complete: the full range is visible, shown green
                .story("Complete", context -> width400(
                        progressBar(Progress.boundedRange(0.0, 100.0, 0.0, 100.0)).getWidget()));
    }

    private static Widget clickable(final StoryContext context) {
        final ProgressPresenter presenter = progressBar(Progress.boundedRange(CLICKABLE_UPPER_BOUND, 100.0, 200.0));
        final Label display = new Label("Click the bar…");
        display.getElement().getStyle().setProperty("marginTop", "8px");
        display.getElement().getStyle().setProperty("fontSize", "12px");
        final Spy onClick = context.fn(ON_CLICK);
        // Differs from React: Stroom's click handler is given the clicked position in the
        // progress's units (here 0-500), not a percentage, so it is turned back into one
        presenter.setClickHandler(value -> {
            final double percentage = value / CLICKABLE_UPPER_BOUND * 100;
            onClick.call(percentage);
            display.setText("Clicked at " + NumberFormat.getFormat("0.0").format(percentage) + "%");
        });
        final FlowPanel panel = width400(presenter.getWidget());
        panel.add(display);
        return panel;
    }

    private static ProgressPresenter progressBar(final Progress progress) {
        final ProgressPresenter presenter = new ProgressPresenter(new SimpleEventBus(),
                new ProgressViewImpl(GWT.create(ProgressViewImpl.Binder.class)));
        presenter.setProgress(progress);
        return presenter;
    }

    /// `<div style={{width: 400}}>`.
    private static FlowPanel width400(final Widget widget) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("width", "400px");
        panel.add(widget);
        return panel;
    }
}
