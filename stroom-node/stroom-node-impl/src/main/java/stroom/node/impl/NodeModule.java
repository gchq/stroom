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
import stroom.entity.shared.EntityEvent.Handler;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.DBTableService;
import stroom.node.shared.UpdateNodeAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class NodeModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DBTableService.class).to(DBTableServiceImpl.class);
        bind(NodeInfo.class).to(NodeInfoImpl.class);
        bind(NodeService.class).to(NodeServiceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(UpdateNodeAction.class, UpdateNodeHandler.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(NodeServiceImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(NodeServiceImpl.class);
    }
}