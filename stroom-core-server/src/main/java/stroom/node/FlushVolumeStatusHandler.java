/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.node;

import stroom.entity.cluster.FlushServiceClusterTask;
import stroom.node.shared.FlushVolumeStatusAction;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = FlushVolumeStatusAction.class)
class FlushVolumeStatusHandler extends AbstractTaskHandler<FlushVolumeStatusAction, VoidResult> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final Security security;

    @Inject
    FlushVolumeStatusHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                             final Security security) {
        this.dispatchHelper = dispatchHelper;
        this.security = security;
    }

    @Override
    public VoidResult exec(final FlushVolumeStatusAction action) {
        return security.secureResult(() -> {
            final FlushServiceClusterTask clusterTask = new FlushServiceClusterTask(action.getUserToken(), action.getTaskName(), VolumeService.class);

            dispatchHelper.execAsync(clusterTask, TargetType.ACTIVE);
            return new VoidResult();
        });
    }
}
