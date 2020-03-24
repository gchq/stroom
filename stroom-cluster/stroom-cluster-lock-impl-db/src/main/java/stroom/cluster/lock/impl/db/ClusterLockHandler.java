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

package stroom.cluster.lock.impl.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import java.net.ConnectException;
import java.net.MalformedURLException;


class ClusterLockHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLockHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final SecurityContext securityContext;

    @Inject
    ClusterLockHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                       final SecurityContext securityContext) {
        this.dispatchHelper = dispatchHelper;
        this.securityContext = securityContext;
    }

    public Boolean exec(final ClusterLockKey key, final ClusterLockStyle lockStyle) {
        return securityContext.secureResult(() -> {
            // If the cluster state is not yet initialised then don't try and call
            // master.
            if (!dispatchHelper.isClusterStateInitialised()) {
                return Boolean.FALSE;
            }

            TargetType targetType = TargetType.MASTER;

            final DefaultClusterResultCollector<Boolean> collector = dispatchHelper
                    .execAsync(new ClusterLockClusterTask(key, lockStyle), TargetType.MASTER);
            final ClusterCallEntry<Boolean> response = collector.getSingleResponse();

            if (response == null) {
                LOGGER.error("No response");
                return Boolean.FALSE;
            }
            if (response.getError() != null) {
                try {
                    throw response.getError();
                } catch (final MalformedURLException e) {
                    LOGGER.warn(response.getError().getMessage());
                } catch (final Throwable e) {
                    if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                        LOGGER.error("Unable to connect to [{}]: {}",
                                String.join(",", collector.getTargetNodes()),
                                response.getError().getMessage());
                    } else {
                        LOGGER.error("Error connecting to [{}]: {}",
                                String.join(",", collector.getTargetNodes()),
                                response.getError().getMessage(), response.getError());
                    }
                }

                return Boolean.FALSE;
            }

            return response.getResult();
        });
    }
}
