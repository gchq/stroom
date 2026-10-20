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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories for Stroom's task counting.
///
/// Stroom has no task counter widget: a `TaskMonitorFactory` such as [SpinnerSmall] counts the
/// tasks its [TaskMonitor]s start and end, and shows its spinner while the count is above zero.
/// The stories start and end tasks with a [SpinnerSmall]'s monitor and show the count (kept by
/// the story, as the spinner's is private) beside it.
public final class TaskCounterStories {

    // Arg names
    private static final String COUNT = "count";

    private TaskCounterStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Feedback/TaskCounter", TaskCounterStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.number(COUNT).description("The number of tasks running (started with the "
                                                           + "spinner's TaskMonitor)."))
                .args(Args.of(COUNT, 0))
                // No tasks running
                .story("Idle", TaskCounterStories::fromArgs)
                .withArgs(Args.of(COUNT, 0))
                // Tasks running, so the spinner shows
                .story("Busy", TaskCounterStories::fromArgs)
                .withArgs(Args.of(COUNT, 3))
                // Start and end tasks, as Stroom's TaskStartEvent and TaskEndEvent do
                .story("Interactive", context -> interactive());
    }

    /// A counter made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Long count = StoryArgs.getLong(context.getArgs(), COUNT);
        final TaskCounter counter = new TaskCounter();
        for (long i = 0; count != null && i < count; i++) {
            counter.start();
        }
        return counter.asWidget();
    }

    private static Widget interactive() {
        final TaskCounter counter = new TaskCounter();
        final InlineLabel isLoading = StoryPanels.note("", "#aaa", "0.8rem");
        final Runnable update = () -> isLoading.setText("isLoading: " + (counter.getCount() > 0));
        update.run();

        // Plain buttons
        final Button start = new Button("Start task");
        start.addClickHandler(event -> {
            counter.start();
            update.run();
        });
        final Button end = new Button("End task");
        end.addClickHandler(event -> {
            counter.end();
            update.run();
        });
        final FlowPanel column = StoryPanels.column(12, counter.asWidget(), isLoading,
                StoryPanels.row(8, start, end));
        column.getElement().getStyle().setProperty("alignItems", "center");
        return column;
    }

    // --------------------------------------------------------------------------------


    /// A [SpinnerSmall] with a label showing how many tasks its monitor has running.
    private static final class TaskCounter {

        private final SpinnerSmall spinner = new SpinnerSmall();
        private final TaskMonitor taskMonitor = spinner.createTaskMonitor();
        private final List<Task> running = new ArrayList<>();
        private final Label label = new Label();
        private final FlowPanel panel = StoryPanels.row(8, spinner, label);

        private TaskCounter() {
            panel.getElement().getStyle().setProperty("flexWrap", "nowrap");
            // Announced by screen readers when it changes
            label.getElement().setAttribute("aria-live", "polite");
            updateLabel();
        }

        private void start() {
            final Task task = new SimpleTask("Task " + (running.size() + 1));
            running.add(task);
            taskMonitor.onStart(task);
            updateLabel();
        }

        private void end() {
            // Only a running task can end, so the count never goes below zero
            if (!running.isEmpty()) {
                taskMonitor.onEnd(running.remove(running.size() - 1));
                updateLabel();
            }
        }

        private int getCount() {
            return running.size();
        }

        private void updateLabel() {
            final int count = running.size();
            label.setText(count + " " + (count == 1
                    ? "task"
                    : "tasks") + " running");
        }

        private Widget asWidget() {
            return panel;
        }
    }
}
