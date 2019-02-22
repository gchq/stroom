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

package stroom.node.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.EntityEvent;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.node.shared.FetchNodeInfoAction;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.GuiceUtil;
import stroom.util.shared.Clearable;

public class NodeHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(NodeInfoImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(ClusterNodeInfoAction.class, ClusterNodeInfoHandler.class)
                .bind(FetchNodeInfoAction.class, FetchNodeInfoHandler.class)
                .bind(FindSystemTableStatusAction.class, FindSystemTableStatusHandler.class)
                .bind(NodeInfoClusterTask.class, NodeInfoClusterHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(NodeInfoImpl.class);
    }
}