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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskListReminder {

    private static final String UNFINISHED = """
            {"tasks": [{"title": "Survey", "status": "completed"}, {"title": "Translate", "status": "in_progress"}]}""";
    private static final String FINISHED = """
            {"tasks": [{"title": "Survey", "status": "completed"}, {"title": "Translate", "status": "completed"}]}""";

    @Test
    void aListLeftUnchangedIsRemindedAboutEveryFewSteps() {
        final TaskListReminder reminder = new TaskListReminder();
        assertThat(reminder.afterStep(List.of(call(TaskListTool.NAME, UNFINISHED)))).isFalse();

        assertThat(steps(reminder, TaskListReminder.STEPS_BEFORE_REMINDER - 1)).isFalse();
        assertThat(reminder.afterStep(List.of(call("read_stream", "{}")))).isTrue();
        // Then not again straight away.
        assertThat(steps(reminder, TaskListReminder.STEPS_BEFORE_REMINDER - 1)).isFalse();
        assertThat(reminder.afterStep(List.of(call("read_stream", "{}")))).isTrue();
    }

    @Test
    void anUpdateStartsTheCountAgain() {
        final TaskListReminder reminder = new TaskListReminder();
        reminder.afterStep(List.of(call(TaskListTool.NAME, UNFINISHED)));
        steps(reminder, TaskListReminder.STEPS_BEFORE_REMINDER - 1);

        // Updated alongside another call.
        assertThat(reminder.afterStep(List.of(call("read_stream", "{}"), call(TaskListTool.NAME, UNFINISHED))))
                .isFalse();
        assertThat(steps(reminder, TaskListReminder.STEPS_BEFORE_REMINDER - 1)).isFalse();
    }

    @Test
    void thereIsNoReminderWithoutAListOrOnceItIsDone() {
        final TaskListReminder noList = new TaskListReminder();
        assertThat(steps(noList, TaskListReminder.STEPS_BEFORE_REMINDER * 2)).isFalse();

        final TaskListReminder done = new TaskListReminder();
        done.afterStep(List.of(call(TaskListTool.NAME, UNFINISHED)));
        done.afterStep(List.of(call(TaskListTool.NAME, FINISHED)));
        assertThat(steps(done, TaskListReminder.STEPS_BEFORE_REMINDER * 2)).isFalse();
    }

    @Test
    void aFailedUpdateLeavesTheListAsItWas() {
        final TaskListReminder reminder = new TaskListReminder();
        reminder.afterStep(List.of(call(TaskListTool.NAME, UNFINISHED)));
        reminder.afterStep(List.of(call(TaskListTool.NAME, "not json")));

        assertThat(steps(reminder, TaskListReminder.STEPS_BEFORE_REMINDER)).isTrue();
    }

    // Takes steps without updating the list, and returns true if any of them gave a reminder.
    private static boolean steps(final TaskListReminder reminder, final int count) {
        boolean reminded = false;
        for (int i = 0; i < count; i++) {
            reminded |= reminder.afterStep(List.of(call("read_stream", "{}")));
        }
        return reminded;
    }

    private static ToolExecutionRequest call(final String name, final String arguments) {
        return ToolExecutionRequest.builder().id(name).name(name).arguments(arguments).build();
    }
}
