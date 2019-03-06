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

package stroom.job.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.ServerTask;
import stroom.task.shared.Task;
import stroom.util.shared.VoidResult;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ScheduledTask extends ServerTask<VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTask.class);

    private final String taskName;
    private final Consumer<Task> method;
    private final AtomicBoolean running;

    public ScheduledTask(final String taskName,
                         final Consumer<Task> method,
                         final AtomicBoolean running) {
        this.taskName = taskName;
        this.method = method;
        this.running = running;
    }

    public void exec(final Task<?> task) {
        try {
            //TODO: debug logging
//            LOGGER.debug(message + " " + methodReference.getClazz().getName() + "." + methodReference.getMethod().getName());

            method.accept(task);
        } catch (final RuntimeException e) {
            LOGGER.error("Error calling {}", taskName, e);
        } finally {
            running.set(false);
        }
    }

    @Override
    public String getTaskName() {
        return taskName;
    }
}
