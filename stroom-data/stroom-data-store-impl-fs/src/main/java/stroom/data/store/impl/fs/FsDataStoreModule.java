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

package stroom.data.store.impl.fs;

import stroom.data.store.api.Store;
import stroom.data.store.impl.DataStoreMaintenanceService;
import stroom.data.store.impl.fs.api.FsVolumeResource;
import stroom.meta.api.AttributeMapFactory;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class FsDataStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DataStoreMaintenanceService.class).to(FsDataStoreMaintenanceService.class);
        bind(Store.class).to(FsStore.class);
        bind(AttributeMapFactory.class).to(FsStore.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(FsVolumeService.class);

        RestResourcesBinder.create(binder())
                .bindResource(FsVolumeResource.class);

        ServletBinder.create(binder())
                .bind(EchoServlet.class);
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