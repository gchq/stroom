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

import java.util.concurrent.Executor;

public interface TaskProducer extends Comparable<TaskProducer> {
    /**
     * Get the executor to use to execute the provided runnable.
     * @return
     */
    Executor getExecutor();

    /**
     * Get the next task to execute or null if the producer has reached a concurrent execution limit or no further tasks
     * are available.
     *
     * @return The next task to execute or null if no tasks are available at this time.
     */
    Runnable next();

    /**
     * When an executor has finished executing a task return it to the producer so it can be marked complete and the
     * producer can decrement the current execution count.
     *
     * @param task The task that has been completed.
     */
    void complete(Runnable task);
}
