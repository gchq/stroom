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

package stroom.task.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.task.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TaskProgress;

import javax.inject.Inject;

@TaskHandlerBean(task = TerminateTaskClusterTask.class)
class TerminateTaskClusterHandler extends AbstractTaskHandler<TerminateTaskClusterTask, BaseResultList<TaskProgress>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminateTaskClusterHandler.class);

    private final TaskManager taskManager;
    private final Security security;

    @Inject
    TerminateTaskClusterHandler(final TaskManager taskManager,
                                final Security security) {
        this.taskManager = taskManager;
        this.security = security;
    }

    @Override
    public BaseResultList<TaskProgress> exec(final TerminateTaskClusterTask task) {
        return security.secureResult(() -> {
            BaseResultList<TaskProgress> taskedKilled = null;

            final FindTaskCriteria criteria = task.getCriteria();
            if (criteria != null) {
                LOGGER.debug("exec() - {}", criteria.toString());

                // Terminate tasks on this node
                taskedKilled = taskManager.terminate(criteria, task.isKill());
            }

            return taskedKilled;
        });
    }
}
