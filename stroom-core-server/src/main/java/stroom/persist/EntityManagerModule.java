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

package stroom.persist;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import stroom.entity.EntityModule;
import stroom.entity.StroomEntityManager;
import stroom.entity.StroomEntityManagerImpl;
import stroom.entity.event.EntityEventModule;

/**
 * Configures anything related to persistence, e.g. transaction management, the
 * entity manager factory, data sources.
 */
public class EntityManagerModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DataSourceModule());
        install(new EntityModule());
        install(new EntityEventModule());

        bind(PersistService.class).to(PersistServiceImpl.class);
        bind(EntityManagerSupport.class).to(EntityManagerSupportImpl.class);
        bind(StroomEntityManager.class).to(StroomEntityManagerImpl.class);

        bind(Object.class).annotatedWith(Names.named("dataSource")).toProvider(DataSourceProvider.class);
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
