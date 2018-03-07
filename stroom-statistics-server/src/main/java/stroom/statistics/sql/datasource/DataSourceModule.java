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

package stroom.statistics.sql.datasource;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.event.EntityEvent;
import stroom.entity.event.EntityEvent.Handler;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;

public class DataSourceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StatisticStoreEntityService.class).to(StatisticStoreEntityServiceImpl.class);
        bind(StatisticStoreCache.class).to(StatisticsDataSourceCacheImpl.class);
        bind(StatisticsDataSourceProvider.class).to(StatisticsDataSourceProviderImpl.class);
        bind(StatisticStoreValidator.class).to(StatisticsDataSourceValidatorImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(StatisticsDataSourceCacheImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.statistics.sql.datasource.StatisticStoreEntityServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.statistics.sql.datasource.StatisticStoreEntityServiceImpl.class);
    }
}