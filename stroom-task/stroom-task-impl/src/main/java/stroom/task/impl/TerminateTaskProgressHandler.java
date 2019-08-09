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

package stroom.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.TargetType;
import stroom.cluster.task.api.TerminateTaskClusterTask;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.shared.TerminateTaskProgressAction;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class TerminateTaskProgressHandler extends AbstractTaskHandler<TerminateTaskProgressAction, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminateTaskProgressHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final SecurityContext securityContext;

    @Inject
    TerminateTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                                 final SecurityContext securityContext) {
        this.dispatchHelper = dispatchHelper;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final TerminateTaskProgressAction action) {
        return securityContext.secureResult(() -> {
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
