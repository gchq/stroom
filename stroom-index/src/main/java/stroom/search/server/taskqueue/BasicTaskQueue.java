/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.server.taskqueue;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import stroom.util.shared.Task;

public class BasicTaskQueue implements TaskQueue {
    private static final int DEFAULT_CAPACITY = 1000;

    private final LinkedBlockingDeque<Task<?>> deque;

    public BasicTaskQueue() {
        this(DEFAULT_CAPACITY);
    }

    public BasicTaskQueue(final int capacity) {
        deque = new LinkedBlockingDeque<>(capacity);
    }

    @Override
    public boolean offer(final Task<?> task) {
        return deque.offer(task);
    }

    @Override
    public boolean offer(final Task<?> task, final long timeout, final TimeUnit unit) throws InterruptedException {
        return deque.offer(task, timeout, unit);
    }

    @Override
    public Task<?> poll() {
        return deque.poll();
    }

    @Override
    public Task<?> poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        return deque.poll(timeout, unit);
    }
}
