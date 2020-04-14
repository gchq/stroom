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

package stroom.task.api;

import stroom.task.shared.TaskId;
import stroom.util.shared.HasTerminate;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public interface TaskContext extends HasTerminate {
    void setName(String name);

    void info(Supplier<String> messageSupplier);

    /**
     * Get the task id of this context.
     *
     * @return The task id of this context.
     */
    TaskId getTaskId();

    /**
     * Wrap a supplier in a sub task context that will be used when the supplier is executed.
     *
     * @param supplier The supplier to wrap.
     * @return A task context wrapped supplier.
     */
    <U> WrappedSupplier<U> sub(Supplier<U> supplier);

    /**
     * Wrap a runnable in the task context that will be used when the runnable is executed.
     *
     * @param runnable The runnable to wrap.
     * @return A task context wrapped runnable.
     */
    WrappedRunnable sub(Runnable runnable);

    class WrappedSupplier<U> implements Supplier<U> {
        private final TaskContext taskContext;
        private final Supplier<U> supplier;

        public WrappedSupplier(final TaskContext taskContext,
                               final Supplier<U> supplier) {
            this.taskContext = taskContext;
            this.supplier = supplier;
        }

        public TaskContext getTaskContext() {
            return taskContext;
        }

        @Override
        public U get() {
            return supplier.get();
        }
    }

    class WrappedRunnable implements Runnable {
        private final TaskContext taskContext;
        private final Runnable runnable;

        public WrappedRunnable(final TaskContext taskContext,
                               final Runnable runnable) {
            this.taskContext = taskContext;
            this.runnable = runnable;
        }

        public TaskContext getTaskContext() {
            return taskContext;
        }

        @Override
        public void run() {
            runnable.run();
        }
    }
}
