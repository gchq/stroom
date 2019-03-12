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

package stroom.cache.impl;

import stroom.cache.shared.CacheClearAction;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.task.api.AbstractTaskHandler;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.TargetType;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class CacheClearHandler extends AbstractTaskHandler<CacheClearAction, VoidResult> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final Security security;

    @Inject
    CacheClearHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                      final Security security) {
        this.dispatchHelper = dispatchHelper;
        this.security = security;
    }

    @Override
    public VoidResult exec(final CacheClearAction action) {
        return security.secureResult(PermissionNames.MANAGE_CACHE_PERMISSION, () -> {
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.getName().setString(action.getCacheName());

            final CacheClearClusterTask clusterTask = new CacheClearClusterTask(
                    action.getUserToken(),
                    action.getTaskName(),
                    criteria);

            if (action.getNodeName() != null) {
                dispatchHelper.execAsync(clusterTask, action.getNodeName());
            } else {
                dispatchHelper.execAsync(clusterTask, TargetType.ACTIVE);
            }
            return new VoidResult();
        });
    }
}
