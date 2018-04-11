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

package stroom.statistics.sql.search;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import stroom.entity.shared.Clearable;
import stroom.query.common.v2.SearchResponseCreatorCacheFactory;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.query.common.v2.StoreFactory;
import stroom.statistics.sql.StatisticsQueryService;

public class SQLStatisticSearchModule extends AbstractModule {
    @Override
    protected void configure() {
        // TODO using named bindings is a bit grim, consider finding a better way
        bind(StoreFactory.class)
                .annotatedWith(Names.named("sqlStatisticStoreFactory"))
                .to(SqlStatisticStoreFactory.class);

        bind(SearchResponseCreatorManager.class)
                .annotatedWith(Names.named("sqlStatisticsSearchResponseCreatorManager"))
                .to(SqlStatisticsSearchResponseCreatorManager.class)
                .asEagerSingleton();

        bind(SearchResponseCreatorCacheFactory.class)
                .annotatedWith(Names.named("sqlStatisticsInMemorySearchResponseCreatorCacheFactory"))
                .to(SqlStatisticsInMemorySearchResponseCreatorCacheFactory.class);

        bind(StatisticsQueryService.class).to(StatisticsQueryServiceImpl.class);
        bind(StatisticsSearchService.class).to(StatisticsSearchServiceImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(SqlStatisticsSearchResponseCreatorManager.class);
    }
}