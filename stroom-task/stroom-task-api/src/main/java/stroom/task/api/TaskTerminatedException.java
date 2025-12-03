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

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.shared.EntityServiceException;

import java.nio.channels.ClosedByInterruptException;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import javax.xml.transform.TransformerException;

public class TaskTerminatedException extends EntityServiceException {

    /**
     * Stop is true if the task manager is shutting down and no more tasks
     * should be submitted. This is useful in situations where tasks are queued
     * for execution and we want to know not to submit further tasks,
     */
    public TaskTerminatedException() {
        super("Task terminated");
    }

    public static Optional<TaskTerminatedException> unwrap(final Throwable e) {
        if (e != null) {
            if (e instanceof final TaskTerminatedException taskTerminatedException) {
                return Optional.of(taskTerminatedException);
            } else if (e instanceof InterruptedException
                    || e instanceof ClosedByInterruptException
                    || e instanceof UncheckedInterruptedException
                    || e instanceof CancellationException) {
                return Optional.of(new TaskTerminatedException());
            } else if (e instanceof final TransformerException transformerEx) {
                if (transformerEx.getException() instanceof final TaskTerminatedException taskTerminatedEx) {
                    return Optional.of(taskTerminatedEx);
                }
            }

            if (e.getCause() != null) {
                return unwrap(e.getCause());
            }
        }
        return Optional.empty();
    }
}
