/*
 * Copyright 2016-2026 Crown Copyright
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


import org.slf4j.Logger;

import java.util.function.Predicate;

public class TaskUtil {

    private TaskUtil() {

    }

    /**
     * Creates a Predicate for use in {@link java.util.stream.Stream#takeWhile(Predicate)} or similar.
     * The predicate returns true if the thread is not interrupted and the taskContext is not terminated.
     *
     * @param logger The logger to log an info message on if the taskContext is terminated or the thread
     *               is interrupted.
     */
    public static Predicate<Object> createTaskTerminatedCheck(final TaskContext taskContext,
                                                              final Logger logger) {
        return ignored -> {
            if (taskContext != null && taskContext.isTerminated()) {
                logger.info("Task is terminated: '{}'", taskContext);
                return false;
            } else if (Thread.currentThread().isInterrupted()) {
                logger.info("Task thread is interrupted: '{}'", taskContext);
                return false;
            } else {
                return true;
            }
        };
    }
}
