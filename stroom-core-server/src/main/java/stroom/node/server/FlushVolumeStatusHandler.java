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

package stroom.node.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.cluster.FlushServiceClusterTask;
import stroom.node.shared.FlushVolumeStatusAction;
import stroom.node.shared.VolumeService;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = FlushVolumeStatusAction.class)
@Scope(StroomScope.TASK)
public class FlushVolumeStatusHandler extends AbstractTaskHandler<FlushVolumeStatusAction, VoidResult> {
    @Resource
    private ClusterDispatchAsyncHelper dispatchHelper;

    @Override
    public VoidResult exec(final FlushVolumeStatusAction action) {
        final FlushServiceClusterTask clusterTask = new FlushServiceClusterTask(action.getUserToken(), action.getTaskName(), VolumeService.class);

        dispatchHelper.execAsync(clusterTask, TargetType.ACTIVE);
        return new VoidResult();
    }
}
