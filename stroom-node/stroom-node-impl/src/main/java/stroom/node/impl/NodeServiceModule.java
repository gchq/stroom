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
import stroom.util.entity.EntityTypeBinder;
import stroom.util.entity.FindService;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.Node;
import stroom.persist.EntityManagerModule;

public class NodeServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(NodeInfo.class).to(NodeInfoImpl.class);
        bind(NodeService.class).to(NodeServiceImpl.class);

        EntityTypeBinder.create(binder())
                .bind(Node.ENTITY_TYPE, NodeServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(NodeServiceImpl.class);
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