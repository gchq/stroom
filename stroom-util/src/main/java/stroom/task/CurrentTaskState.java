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

package stroom.task;

import stroom.util.shared.Task;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CurrentTaskState {
    private static final ThreadLocal<Deque<TaskThread>> THREAD_LOCAL = ThreadLocal.withInitial(ArrayDeque::new);

    private CurrentTaskState() {
        // Utility.
    }

    static void pushState(final TaskThread taskThread) {
        final Deque<TaskThread> deque = THREAD_LOCAL.get();
        deque.push(taskThread);
    }

    static TaskThread popState() {
        final Deque<TaskThread> deque = THREAD_LOCAL.get();
        return deque.pop();
    }

    private static TaskThread currentState() {
        final Deque<TaskThread> deque = THREAD_LOCAL.get();
        return deque.peek();
    }

    public static Task<?> currentTask() {
        final TaskThread taskThread = currentState();
        if (taskThread != null) {
            return taskThread.getTask();
        }
        return null;
    }

    static void setName(final String name) {
        final TaskThread taskThread = currentState();
        if (taskThread != null) {
            taskThread.setName(name);
        }
    }

    static void info(final Object... args) {
        final TaskThread taskThread = currentState();
        if (taskThread != null) {
            taskThread.info(args);
        }
    }

    static void terminate() {
        final TaskThread taskThread = currentState();
        if (taskThread != null) {
            taskThread.terminate();
        }
    }
}