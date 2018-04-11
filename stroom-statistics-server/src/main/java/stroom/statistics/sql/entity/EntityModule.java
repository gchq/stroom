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

package stroom.statistics.sql.entity;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.FindService;
import stroom.entity.event.EntityEvent;
import stroom.entity.event.EntityEvent.Handler;
import stroom.entity.shared.Clearable;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.statistics.shared.StatisticStoreEntity;

public class EntityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StatisticStoreEntityService.class).to(StatisticStoreEntityServiceImpl.class);
        bind(StatisticStoreCache.class).to(StatisticsDataSourceCacheImpl.class);
        bind(StatisticsDataSourceProvider.class).to(StatisticsDataSourceProviderImpl.class);
        bind(StatisticStoreValidator.class).to(StatisticsDataSourceValidatorImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(StatisticsDataSourceCacheImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(StatisticsDataSourceCacheImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(StatisticStoreEntityServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(StatisticStoreEntityServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(StatisticStoreEntity.ENTITY_TYPE).to(StatisticStoreEntityServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(StatisticStoreEntityServiceImpl.class);
    }
}