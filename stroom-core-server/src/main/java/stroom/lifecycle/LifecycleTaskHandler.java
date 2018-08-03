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

package stroom.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = LifecycleTask.class)
class LifecycleTaskHandler extends AbstractTaskHandler<LifecycleTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleTaskHandler.class);

    private final Security security;

    @Inject
    LifecycleTaskHandler(final Security security) {
        this.security = security;
    }

    @Override
    public VoidResult exec(final LifecycleTask task) {
        return security.secureResult(() -> {
            try {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.debug("exec() - >>> {}", task.getTaskName());
                task.getExecutable().exec(task);
                LOGGER.debug("exec() - <<< {} took {}", task.getTaskName(), logExecutionTime);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return new VoidResult();
        });
    }
}
