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

package stroom.cache;

import stroom.cache.shared.CacheClearAction;
import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.entity.cluster.FindClearServiceClusterTask;
import stroom.security.Secured;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = CacheClearAction.class)
@Secured(CacheRow.MANAGE_CACHE_PERMISSION)
class CacheClearHandler extends AbstractTaskHandler<CacheClearAction, VoidResult> {
    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    CacheClearHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        this.dispatchHelper = dispatchHelper;
    }

    @Override
    public VoidResult exec(final CacheClearAction action) {
        final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
        criteria.getName().setString(action.getCacheName());

        final FindClearServiceClusterTask<FindCacheInfoCriteria> clusterTask = new FindClearServiceClusterTask<>(
                action.getUserToken(), action.getTaskName(), StroomCacheManager.class, criteria);

        if (action.getNode() != null) {
            dispatchHelper.execAsync(clusterTask, action.getNode());
        } else {
            dispatchHelper.execAsync(clusterTask, TargetType.ACTIVE);
        }
        return new VoidResult();
    }
}
