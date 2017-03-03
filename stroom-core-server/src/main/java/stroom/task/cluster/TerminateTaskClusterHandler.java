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

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;

import stroom.entity.shared.BaseResultList;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TaskProgress;

@TaskHandlerBean(task = TerminateTaskClusterTask.class)
@Scope(value = StroomScope.TASK)
public class TerminateTaskClusterHandler
        extends AbstractTaskHandler<TerminateTaskClusterTask, BaseResultList<TaskProgress>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminateTaskClusterHandler.class);

    @Resource
    private TaskManager taskManager;

    @Override
    public BaseResultList<TaskProgress> exec(final TerminateTaskClusterTask task) {
        BaseResultList<TaskProgress> taskedKilled = null;

        final FindTaskCriteria criteria = task.getCriteria();
        if (criteria != null) {
            LOGGER.debug("exec() - {}", criteria.toString());

            // Terminate tasks on this node
            taskedKilled = taskManager.terminate(criteria, task.isKill());
        }

        return taskedKilled;
    }
}
