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

import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEvent.Handler;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class NodeModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(NodeInfo.class).to(NodeInfoImpl.class);
        bind(NodeService.class).to(NodeServiceImpl.class);
        bind(NodeResource.class).to(NodeResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(NodeServiceImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(NodeServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(NodeResourceImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Node.class, NodeObjectInfoProvider.class);
    }
}