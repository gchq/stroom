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

package stroom.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.cluster.TerminateTaskClusterTask;
import stroom.task.shared.TerminateTaskProgressAction;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = TerminateTaskProgressAction.class)
class TerminateTaskProgressHandler extends AbstractTaskHandler<TerminateTaskProgressAction, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminateTaskProgressHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final Security security;

    @Inject
    TerminateTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                                 final Security security) {
        this.dispatchHelper = dispatchHelper;
        this.security = security;
    }

    @Override
    public VoidResult exec(final TerminateTaskProgressAction action) {
        return security.secureResult(() -> {
            final TerminateTaskClusterTask terminateTask = new TerminateTaskClusterTask(action.getUserToken(), action.getTaskName(), action.getCriteria(), action.isKill());
            if (action.getCriteria() != null && action.getCriteria().isConstrained()) {
                // Terminate matching tasks.
                dispatchHelper.execAsync(terminateTask, TargetType.ACTIVE);
            }

            LOGGER.info("exec() - Finished");

            return VoidResult.INSTANCE;
        });
    }
}
