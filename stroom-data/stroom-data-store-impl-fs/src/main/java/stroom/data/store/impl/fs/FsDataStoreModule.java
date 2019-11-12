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

import com.google.inject.AbstractModule;
import stroom.data.store.api.Store;
import stroom.data.store.impl.DataStoreMaintenanceService;
import stroom.data.store.impl.fs.api.FsVolumeResource;
import stroom.util.shared.RestResource;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class FsDataStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DataStoreMaintenanceService.class).to(FsDataStoreMaintenanceService.class);
        bind(Store.class).to(FsStore.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(FsVolumeService.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(FsVolumeResource.class);
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