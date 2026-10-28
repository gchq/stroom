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
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.progress.client.Progress;
import stroom.widget.progress.client.component.SourceProgressBar;
import stroom.widget.text.client.Label;

import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [SourceProgressBar], the sliding window progress bar of the source editor.
public final class SourceProgressBarStories {

    private static final String ON_CLICK = "onClick";
    private static final double CLICKABLE_UPPER_BOUND = 500.0;
    private static final String WINDOW = ".source-progress-bar__window";

    private SourceProgressBarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // The stories render fixed progress
        registry.component("Widgets/Feedback/SourceProgressBar", SourceProgressBarStories.class)
                .layout(StoryLayout.PADDED)
                // A simple percentage progress bar at 65% (not clickable, so in the disabled colour)
                .story("Basic", context -> width400(sourceProgressBar(Progress.simplePercentage(65.0))))
                .withPlay(play -> play.expect(play.querySelector(WINDOW))
                        .toHaveClass("source-progress-bar__window--disabled"))
                // A sliding window: showing 200-350 of a 0-1000 range
                .story("SlidingWindow", context -> width400(
                        sourceProgressBar(Progress.boundedRange(1000.0, 200.0, 350.0))))
                // Clickable: click to move the visible window; the clicked % is shown
                .story("Clickable", SourceProgressBarStories::clickable)
                .withPlay(play -> {
                    play.expect(play.querySelector(".source-progress-bar__track"))
                            .toHaveClass("source-progress-bar__track--clickable");
                    play.click(play.querySelector(".source-progress-bar__track"));
                    play.expect(play.spy(ON_CLICK)).toHaveBeenCalled();
                    play.expect(play.getByText(TextMatch.startingWith("Clicked at "))).toBeInTheDocument();
                })
                // Complete: the full range is visible, shown green
                .story("Complete", context -> width400(
                        sourceProgressBar(Progress.boundedRange(0.0, 100.0, 0.0, 100.0))))
                .withPlay(play -> play.expect(play.querySelector(WINDOW))
                        .toHaveClass("source-progress-bar__window--complete"))
                // Unbounded: how much there is isn't known (e.g. a stream still being read), shown
                // amber, with its window a fixed size
                .story("Unbounded", context -> width400(
                        sourceProgressBar(Progress.unboundedRange(200.0, 350.0))))
                .withPlay(play -> play.expect(play.querySelector(WINDOW))
                        .toHaveClass("source-progress-bar__window--unbounded"));
    }

    private static Widget clickable(final StoryContext context) {
        final SourceProgressBar bar = sourceProgressBar(Progress.boundedRange(CLICKABLE_UPPER_BOUND, 100.0, 200.0));
        final Label display = new Label("Click the bar…");
        display.getElement().getStyle().setProperty("marginTop", "8px");
        display.getElement().getStyle().setProperty("fontSize", "12px");
        final Spy onClick = context.fn(ON_CLICK);
        // Stroom's click handler is given the clicked position in the progress's units (here
        // 0-500), not a percentage, so it is turned back into one
        bar.setOnClick(value -> {
            final double percentage = value / CLICKABLE_UPPER_BOUND * 100;
            onClick.call(percentage);
            display.setText("Clicked at " + NumberFormat.getFormat("0.0").format(percentage) + "%");
        });
        final FlowPanel panel = width400(bar);
        panel.add(display);
        return panel;
    }

    private static SourceProgressBar sourceProgressBar(final Progress progress) {
        final SourceProgressBar bar = new SourceProgressBar();
        bar.setProgress(progress);
        return bar;
    }

    /// `<div style={{width: 400}}>`.
    private static FlowPanel width400(final Widget widget) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("width", "400px");
        panel.add(widget);
        return panel;
    }
}
