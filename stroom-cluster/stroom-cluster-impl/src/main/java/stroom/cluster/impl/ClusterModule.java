/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.cluster.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class ClusterModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClusterNodeManager.class).to(ClusterNodeManagerImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(ClusterNodeManagerImpl.class);

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(ClusterNodeManagerInit.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    private static class ClusterNodeManagerInit extends RunnableWrapper {

        @Inject
        ClusterNodeManagerInit(final ClusterNodeManagerImpl clusterNodeManager) {
            super(clusterNodeManager::init);
        }
    }
}
