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

package stroom.task.api;

import stroom.task.shared.TaskId;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class SimpleTaskContext implements TaskContext {

    private final AtomicBoolean terminated = new AtomicBoolean();

    @Override
    public void info(final Supplier<String> messageSupplier) {
    }

    @Override
    public TaskId getTaskId() {
        return null;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean isTerminated() {
        return terminated.get();
    }

    public void terminate() {
        terminated.set(true);
    }
}
