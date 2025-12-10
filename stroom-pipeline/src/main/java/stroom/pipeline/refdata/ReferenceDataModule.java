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

package stroom.pipeline.refdata;

import stroom.pipeline.factory.PipelineElementModule;
import stroom.pipeline.refdata.store.RefDataStoreModule;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.searchable.api.Searchable;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

public class ReferenceDataModule extends PipelineElementModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ReferenceDataService.class).to(ReferenceDataServiceImpl.class);
        bind(ReferenceDataLoader.class).to(ReferenceDataLoaderImpl.class);
        bind(ContextDataLoader.class).to(ContextDataLoaderImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(EffectiveStreamCache.class);

        HasSystemInfoBinder.create(binder())
                .bind(EffectiveStreamCache.class);

        RestResourcesBinder.create(binder())
                .bind(ReferenceDataResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(ReferenceDataServiceImpl.class);
        GuiceUtil.buildMapBinder(binder(), Searchable.class)
                .addBinding(ReferenceDataServiceImpl.class);

        install(new RefDataStoreModule());
    }

    @Override
    protected void configureElements() {
        bindElement(ReferenceDataFilter.class);
    }
}
