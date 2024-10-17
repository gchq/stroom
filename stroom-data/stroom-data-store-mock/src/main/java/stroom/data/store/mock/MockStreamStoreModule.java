/*
 * Copyright 2018-2024 Crown Copyright
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

package stroom.data.store.mock;

import stroom.data.store.api.DataService;
import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.api.Store;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class MockStreamStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Store.class).to(MockStore.class);
        bind(DataService.class).to(MockDataService.class);
        bind(FsVolumeGroupService.class).to(MockFsVolumeGroupService.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(MockStore.class);
    }
}
