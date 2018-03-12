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

package stroom.statistics.stroomstats.entity;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.entity.FindService;
import stroom.entity.StroomEntityManager;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.stats.shared.StroomStatsStoreEntity;

public class StroomStatsEntityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StroomStatsStoreEntityService.class).to(StroomStatsStoreEntityServiceImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.statistics.stroomstats.entity.StroomStatsStoreEntityServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.statistics.stroomstats.entity.StroomStatsStoreEntityServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(StroomStatsStoreEntity.ENTITY_TYPE).to(stroom.statistics.stroomstats.entity.StroomStatsStoreEntityServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(stroom.statistics.stroomstats.entity.StroomStatsStoreEntityServiceImpl.class);
    }
}