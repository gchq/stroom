/*
 * Copyright 2017 Crown Copyright
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

package stroom.task.server;

import stroom.util.shared.Monitor;
import stroom.util.shared.Task;

import java.util.Deque;
import java.util.LinkedList;

final class CurrentTaskState {
    private static final ThreadLocal<Deque<TaskState>> THREAD_LOCAL = InheritableThreadLocal.withInitial(LinkedList::new);

    private static class TaskState {
        private final Task<?> task;
        private final Monitor monitor;

        TaskState(final Task<?> task, final Monitor monitor) {
            this.task = task;
            this.monitor = monitor;
        }
    }

    private CurrentTaskState() {
        // Utility.
    }

    static void pushState(final Task<?> task, final Monitor monitor) {
        final Deque<TaskState> deque = THREAD_LOCAL.get();
        deque.push(new TaskState(task, monitor));
    }

    static TaskState popState() {
        final Deque<TaskState> deque = THREAD_LOCAL.get();
        return deque.pop();
    }

    private static TaskState currentState() {
        final Deque<TaskState> deque = THREAD_LOCAL.get();
        return deque.peek();
    }

    static Task<?> currentTask() {
        final TaskState taskState = currentState();
        if (taskState != null) {
            return taskState.task;
        }
        return null;
    }

    static void setName(final String name) {
        final TaskState taskState = currentState();
        if (taskState != null) {
            taskState.monitor.setName(name);
        }
    }

    static void info(final Object... args) {
        final TaskState taskState = currentState();
        if (taskState != null) {
            taskState.monitor.info(args);
        }
    }

    static boolean isTerminated() {
        final TaskState taskState = currentState();
        return taskState != null && taskState.task.isTerminated();
    }

    static void terminate() {
        final TaskState taskState = currentState();
        if (taskState != null) {
            taskState.task.terminate();
        }
    }
}