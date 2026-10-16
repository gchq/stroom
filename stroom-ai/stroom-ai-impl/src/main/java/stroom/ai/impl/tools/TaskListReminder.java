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

package stroom.ai.impl.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Map;

/// Reminds the model to keep its task list up to date. Models often write the list when they start and then forget
/// it, so the user sees the first step in progress throughout. Once the model has kept a list with steps still to
/// do, it is reminded after every few steps in which it hasn't updated it. One is made for each run.
public final class TaskListReminder {

    /// How many steps without an update before the model is reminded.
    static final int STEPS_BEFORE_REMINDER = 5;

    /// What the model is told.
    public static final String REMINDER = "Reminder: your task list hasn't changed for a few steps. If you have "
                                          + "finished a step or started another, update it with "
                                          + TaskListTool.NAME + " now, so the user can follow along.";

    private boolean unfinished;
    private int stepsSinceUpdate;

    /// Notes the tool calls the model made in a step.
    ///
    /// @param requests The step's tool calls.
    /// @return True if the model is to be reminded about its task list after this step.
    public boolean afterStep(final List<ToolExecutionRequest> requests) {
        final ToolExecutionRequest update = requests.stream()
                .filter(request -> TaskListTool.NAME.equals(request.name()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (update != null) {
            try {
                unfinished = hasUnfinished(TaskListTool.tasks(update.arguments()));
            } catch (final IllegalArgumentException e) {
                // The call failed, so the list is as it was.
            }
            stepsSinceUpdate = 0;
            return false;
        }
        if (!unfinished) {
            return false;
        }
        stepsSinceUpdate++;
        if (stepsSinceUpdate >= STEPS_BEFORE_REMINDER) {
            stepsSinceUpdate = 0;
            return true;
        }
        return false;
    }

    /// @param tasks A task list as [TaskListTool] stores it.
    /// @return True if any of its steps isn't completed.
    static boolean hasUnfinished(final List<Map<String, String>> tasks) {
        return tasks.stream().anyMatch(task -> !TaskListTool.COMPLETED.equals(task.get(TaskListTool.STATUS)));
    }
}
