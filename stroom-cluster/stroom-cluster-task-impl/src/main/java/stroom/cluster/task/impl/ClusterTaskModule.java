/*
 * Copyright 2018 Crown Copyright
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

package stroom.cluster.task.impl;

import com.google.inject.AbstractModule;
import stroom.cluster.api.ClusterServiceBinder;
import stroom.cluster.task.api.ClusterDispatchAsync;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.ClusterResultCollectorCache;
import stroom.cluster.task.api.ClusterTaskHandlerBinder;
import stroom.cluster.task.api.ClusterWorker;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.cluster.task.api.TerminateTaskClusterTask;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class ClusterTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ClusterDispatchAsync.class).to(ClusterDispatchAsyncImpl.class);
        bind(ClusterDispatchAsyncHelper.class).to(ClusterDispatchAsyncHelperImpl.class);
        bind(ClusterResultCollectorCache.class).to(ClusterResultCollectorCacheImpl.class);
        bind(ClusterWorker.class).to(ClusterWorkerImpl.class);
        bind(TargetNodeSetFactory.class).to(TargetNodeSetFactoryImpl.class);

        ClusterServiceBinder.create(binder())
                .bind(ClusterDispatchAsyncImpl.SERVICE_NAME, ClusterDispatchAsyncImpl.class)
                .bind(ClusterWorkerImpl.SERVICE_NAME, ClusterWorkerImpl.class);

        ClusterTaskHandlerBinder.create(binder())
                .bind(TerminateTaskClusterTask.class, TerminateTaskClusterHandler.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(ClusterResultCollectorCacheImpl.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}