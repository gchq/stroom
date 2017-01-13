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

package stroom.pool.server;

import org.springframework.context.annotation.Scope;
import stroom.pool.shared.PoolClearAction;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = PoolClearAction.class)
@Scope(StroomScope.TASK)
public class PoolClearHandler extends AbstractTaskHandler<PoolClearAction, VoidResult> {
    @Resource
    private ClusterDispatchAsyncHelper dispatchHelper;

    @Override
    public VoidResult exec(final PoolClearAction action) {
        // final FindPoolInfoCriteria criteria = new FindPoolInfoCriteria();
        // criteria.getName().setString(action.getPoolName());
        //
        // final FindClearServiceClusterTask<FindPoolInfoCriteria> clusterTask =
        // new FindClearServiceClusterTask<FindPoolInfoCriteria>(
        // action.getSessionId(), action.getUserId(), action.getTaskName(),
        // PoolManager.class, criteria);
        //
        // if (action.getNode() != null) {
        // dispatchHelper.execAsync(clusterTask, action.getNode());
        // } else {
        // dispatchHelper.execAsync(clusterTask, TargetType.ACTIVE);
        // }
        return new VoidResult();
    }
}
