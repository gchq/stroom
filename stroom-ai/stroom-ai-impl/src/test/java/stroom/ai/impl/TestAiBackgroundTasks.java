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

package stroom.ai.impl;

import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiBackgroundTasks {

    private final TaskManager taskManager = Mockito.mock(TaskManager.class);
    private final AiBackgroundTasks backgroundTasks = new AiBackgroundTasks(() -> taskManager);

    @Test
    void aChatsTasksAreTerminated() {
        final TaskContext download1 = task("1");
        final TaskContext download2 = task("2");
        final TaskContext otherChats = task("3");
        backgroundTasks.started(7, download1);
        backgroundTasks.started(7, download2);
        backgroundTasks.started(8, otherChats);

        assertThat(backgroundTasks.terminate(7)).isEqualTo(2);

        Mockito.verify(taskManager).terminate(download1.getTaskId());
        Mockito.verify(taskManager).terminate(download2.getTaskId());
        Mockito.verify(taskManager, Mockito.never()).terminate(otherChats.getTaskId());
    }

    @Test
    void endedTasksAreNotTerminated() {
        final TaskContext download = task("1");
        backgroundTasks.started(7, download);
        backgroundTasks.ended(7, download);

        assertThat(backgroundTasks.terminate(7)).isZero();
        Mockito.verifyNoInteractions(taskManager);
    }

    @Test
    void aChatWithNoTasksHasNothingToTerminate() {
        assertThat(backgroundTasks.terminate(7)).isZero();
        // Ending a task that was never recorded changes nothing.
        backgroundTasks.ended(7, task("1"));
        assertThat(backgroundTasks.terminate(7)).isZero();
        Mockito.verifyNoInteractions(taskManager);
    }

    private static TaskContext task(final String id) {
        final TaskContext taskContext = Mockito.mock(TaskContext.class);
        Mockito.when(taskContext.getTaskId()).thenReturn(new TaskId(id, null));
        return taskContext;
    }
}
