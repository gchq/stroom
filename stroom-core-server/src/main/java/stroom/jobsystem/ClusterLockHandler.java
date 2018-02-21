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

package stroom.jobsystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedBoolean;

import javax.inject.Inject;
import java.net.MalformedURLException;

@TaskHandlerBean(task = ClusterLockTask.class)
class ClusterLockHandler extends AbstractTaskHandler<ClusterLockTask, SharedBoolean> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLockHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    ClusterLockHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        this.dispatchHelper = dispatchHelper;
    }

    @Override
    public SharedBoolean exec(final ClusterLockTask task) {
        // If the cluster state is not yet initialised then don't try and call
        // master.
        if (!dispatchHelper.isClusterStateInitialised()) {
            return SharedBoolean.wrap(Boolean.FALSE);
        }

        final DefaultClusterResultCollector<SharedBoolean> collector = dispatchHelper
                .execAsync(new ClusterLockClusterTask(task), TargetType.MASTER);
        final ClusterCallEntry<SharedBoolean> response = collector.getSingleResponse();

        if (response == null) {
            LOGGER.error("No response");
            return SharedBoolean.wrap(Boolean.FALSE);
        }
        if (response.getError() != null) {
            try {
                throw response.getError();
            } catch (final MalformedURLException e) {
                LOGGER.warn(response.getError().getMessage());
            } catch (final Throwable e) {
                LOGGER.error(response.getError().getMessage(), response.getError());
            }

            return SharedBoolean.wrap(Boolean.FALSE);
        }

        return response.getResult();
    }
}
