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

package stroom.node;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.event.EntityEvent;
import stroom.entity.shared.Clearable;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.node.shared.FetchNodeInfoAction;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.node.shared.FlushVolumeStatusAction;
import stroom.task.api.TaskHandlerBinder;

public class NodeHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(NodeInfoImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(ClusterNodeInfoAction.class, ClusterNodeInfoHandler.class)
                .bind(FetchNodeInfoAction.class, FetchNodeInfoHandler.class)
                .bind(FindSystemTableStatusAction.class, FindSystemTableStatusHandler.class)
                .bind(FlushVolumeStatusAction.class, FlushVolumeStatusHandler.class)
                .bind(NodeInfoClusterTask.class, NodeInfoClusterHandler.class)
                .bind(FlushVolumeClusterTask.class, FlushVolumeClusterHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(NodeInfoImpl.class);
    }
}