/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.task.impl;

import java.util.ArrayDeque;
import java.util.Deque;

final class CurrentTaskContext {
    private static final ThreadLocal<Deque<TaskContextImpl>> THREAD_LOCAL = ThreadLocal.withInitial(ArrayDeque::new);

    private CurrentTaskContext() {
        // Utility.
    }

    static void pushContext(final TaskContextImpl taskContext) {
        final Deque<TaskContextImpl> deque = THREAD_LOCAL.get();
        deque.push(taskContext);
    }

    static TaskContextImpl popContext() {
        final Deque<TaskContextImpl> deque = THREAD_LOCAL.get();
        return deque.pop();
    }

    static TaskContextImpl currentContext() {
        final Deque<TaskContextImpl> deque = THREAD_LOCAL.get();
        return deque.peek();
    }
}
