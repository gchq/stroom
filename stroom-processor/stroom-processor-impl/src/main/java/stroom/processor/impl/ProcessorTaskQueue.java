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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorTask;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

public class ProcessorTaskQueue {

    private final LinkedBlockingQueue<ProcessorTask> queue = new LinkedBlockingQueue<>();

    public ProcessorTask poll() {
        return queue.poll();
    }

    public boolean addAll(final Collection<? extends ProcessorTask> streamTasks) {
        return queue.addAll(streamTasks);
    }

    public int size() {
        return queue.size();
    }

    public boolean hasItems() {
        return !queue.isEmpty();
    }
}
