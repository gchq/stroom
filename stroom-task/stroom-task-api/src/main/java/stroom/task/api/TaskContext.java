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
import stroom.util.shared.NullSafe;

import org.slf4j.Logger;

import java.util.function.Supplier;

public interface TaskContext extends Terminator {

    void info(Supplier<String> messageSupplier);

    /**
     * Set the messageSupplier on the taskContext and if logger has debug enabled
     * then log the message as well.
     */
    default void info(final Supplier<String> messageSupplier, final Logger logger) {

        info(messageSupplier);

        if (logger != null && logger.isDebugEnabled() && messageSupplier != null) {
            logger.debug("TaskId: {}, info: {}",
                    NullSafe.get(getTaskId(), TaskId::getId),
                    messageSupplier.get());
        }
    }

    /**
     * Get the task id of this context.
     *
     * @return The task id of this context.
     */
    TaskId getTaskId();

    /**
     * Reset submission time etc.
     */
    void reset();
}
