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
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import stroom.entity.CachingEntityManager;
import stroom.entity.EntityModule;
import stroom.entity.FindService;
import stroom.node.shared.Node;
import stroom.persist.EntityManagerModule;
import stroom.properties.PropertyModule;
import stroom.properties.api.StroomPropertyService;
import stroom.security.Security;

public class NodeServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new EntityManagerModule());
        install(new PropertyModule());

        bind(NodeService.class).to(NodeServiceImpl.class);
        bind(NodeServiceGetDefaultNode.class).to(NodeServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(Node.ENTITY_TYPE).to(NodeServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(NodeServiceImpl.class);
    }

    @Provides
    @Named("cachedNodeService")
    public NodeService cachedNodeService(final CachingEntityManager entityManager,
                                         final Security security,
                                         final NodeServiceTransactionHelper nodeServiceTransactionHelper,
                                         final StroomPropertyService propertyService) {
        return new NodeServiceImpl(entityManager, security, nodeServiceTransactionHelper, propertyService);
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