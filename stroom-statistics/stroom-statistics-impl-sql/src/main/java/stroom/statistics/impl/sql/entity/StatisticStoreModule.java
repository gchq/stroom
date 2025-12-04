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

package stroom.statistics.impl.sql.entity;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class StatisticStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(StatisticStoreStore.class).to(StatisticStoreStoreImpl.class);
        bind(StatisticStoreCache.class).to(StatisticsDataSourceCacheImpl.class);
        bind(StatisticStoreValidator.class).to(StatisticsDataSourceValidatorImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(StatisticsDataSourceCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(StatisticsDataSourceCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(StatisticStoreStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(StatisticStoreStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(StatisticStoreStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(StatisticStoreDoc.TYPE, StatisticStoreStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(StatisticResourceImpl.class);
    }
}
