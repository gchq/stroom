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

package stroom.data.store.impl.fs;

import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.api.Store;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class FsDataStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Store.class).to(FsStore.class);
        bind(AttributeMapFactory.class).to(FsStore.class);
        bind(FsVolumeGroupService.class).to(FsVolumeGroupServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(FsVolumeService.class)
                .addBinding(FsVolumeGroupServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(FsVolumeResourceImpl.class)
                .bind(FsVolumeGroupResourceImpl.class);

        ObjectInfoProviderBinder.create(binder())
                .bind(FsVolume.class, FsVolumeObjectInfoProvider.class);

        ServletBinder.create(binder())
                .bind(EchoServlet.class);

        HasSystemInfoBinder.create(binder())
                .bind(FsVolumeService.class);
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
}
