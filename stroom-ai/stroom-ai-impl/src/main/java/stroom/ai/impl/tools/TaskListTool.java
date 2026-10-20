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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/// Lets the agent keep a task list for work with several steps (e.g. onboarding a feed), which the user sees above
/// the chat's input as the agent works through it, so they can see the plan, how far it has got and what is left.
/// Each call gives the whole list. It changes nothing in Stroom.
class TaskListTool extends AbstractBuiltInTool {

    static final String NAME = "task_list";
    static final String TASKS_ARG = "tasks";
    static final String TITLE = "title";
    static final String STATUS = "status";
    static final String NOT_STARTED = "not_started";
    static final String IN_PROGRESS = "in_progress";
    static final String COMPLETED = "completed";
    static final int MAX_TASKS = 20;
    static final int MAX_TITLE_CHARS = 200;

    private final AiService aiService;

    @Inject
    TaskListTool(final AiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema task = ObjectSchema.builder()
                .addStringProperty(TITLE, "What the step is, in a few words.")
                .addEnumProperty(STATUS, List.of(NOT_STARTED, IN_PROGRESS, COMPLETED), "How far along it is.")
                .required(TITLE, STATUS)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description("Keeps a task list for work with several steps, which the user sees as you work. "
                             + "Give the whole list each time: write the steps before you start, keep one step "
                             + "in_progress at a time, and mark each completed as soon as it is done. Don't use "
                             + "it for a single quick step.")
                .parameters(ObjectSchema.builder()
                        .addProperty(TASKS_ARG, ArraySchema.builder().items(task)
                                .description("All the steps, in order (at most " + MAX_TASKS + ").").build())
                        .required(TASKS_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final OptionalInt chatId = context.session().chatId();
        if (chatId.isEmpty()) {
            return ToolResult.text(onlyInAChat(NAME));
        }
        final List<Map<String, String>> tasks;
        try {
            tasks = tasks(request.arguments());
        } catch (final IllegalArgumentException e) {
            return ToolResult.text("Error: " + e.getMessage());
        }
        aiService.storeMessage(chatId.getAsInt(), AiMessageType.TASK_LIST,
                JsonUtil.writeValueAsString(Map.of(TASKS_ARG, tasks), false));
        return ToolResult.text(summary(tasks));
    }

    /// @return The tasks in the arguments, checked.
    /// @throws IllegalArgumentException if they aren't a list of tasks with titles and known statuses.
    static List<Map<String, String>> tasks(final String arguments) {
        final Object given;
        try {
            given = JsonUtil.readValue(arguments, Map.class).get(TASKS_ARG);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Give the tasks as a list.");
        }
        if (!(given instanceof final List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Give the tasks as a list of at least one.");
        }
        if (list.size() > MAX_TASKS) {
            throw new IllegalArgumentException("Give at most " + MAX_TASKS + " tasks.");
        }
        final List<Map<String, String>> tasks = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof final Map<?, ?> map) || !(map.get(TITLE) instanceof final String title)
                || title.isBlank()) {
                throw new IllegalArgumentException("Each task needs a title.");
            }
            final String status = String.valueOf(map.get(STATUS));
            if (!List.of(NOT_STARTED, IN_PROGRESS, COMPLETED).contains(status)) {
                throw new IllegalArgumentException("A task's status must be " + NOT_STARTED + ", " + IN_PROGRESS
                                                   + " or " + COMPLETED + ".");
            }
            final String oneLine = title.strip().replaceAll("\\s+", " ");
            final Map<String, String> task = new LinkedHashMap<>();
            task.put(TITLE, oneLine.length() > MAX_TITLE_CHARS
                    ? oneLine.substring(0, MAX_TITLE_CHARS - 1) + "…"
                    : oneLine);
            task.put(STATUS, status);
            tasks.add(task);
        }
        return tasks;
    }

    private static String summary(final List<Map<String, String>> tasks) {
        final long done = tasks.stream().filter(task -> COMPLETED.equals(task.get(STATUS))).count();
        final String current = tasks.stream()
                .filter(task -> IN_PROGRESS.equals(task.get(STATUS)))
                .map(task -> task.get(TITLE))
                .findFirst()
                .orElse(null);
        return "Task list updated: " + done + " of " + tasks.size() + " done."
               + (current == null
                ? ""
                : " In progress: " + current + ".");
    }

    @Override
    public boolean readsUntrustedContent() {
        // It only repeats what the model gave it.
        return false;
    }
}
