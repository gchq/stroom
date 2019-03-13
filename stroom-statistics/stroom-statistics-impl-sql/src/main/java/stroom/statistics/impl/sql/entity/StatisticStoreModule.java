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

package stroom.statistics.impl.sql.entity;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEvent.Handler;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.GuiceUtil;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.util.shared.Clearable;

public class StatisticStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StatisticStoreStore.class).to(StatisticStoreStoreImpl.class);
        bind(StatisticStoreCache.class).to(StatisticsDataSourceCacheImpl.class);
        bind(StatisticsDataSourceProvider.class).to(StatisticsDataSourceProviderImpl.class);
        bind(StatisticStoreValidator.class).to(StatisticsDataSourceValidatorImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(StatisticsDataSourceCacheImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(StatisticsDataSourceCacheImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(StatisticStoreStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(StatisticStoreStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(StatisticStoreDoc.DOCUMENT_TYPE, StatisticStoreStoreImpl.class);

//        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
//        findServiceBinder.addBinding().to(StatisticStoreStoreImpl.class);
    }
}