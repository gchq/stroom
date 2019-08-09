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

package stroom.statistics.impl.sql.search;

import com.google.inject.AbstractModule;
import stroom.statistics.impl.sql.StatisticsQueryService;
import stroom.util.RestResource;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class SQLStatisticSearchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StatisticsQueryService.class).to(StatisticsQueryServiceImpl.class);
        bind(StatisticsSearchService.class).to(StatisticsSearchServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(SqlStatisticsSearchResponseCreatorManager.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(SqlStatisticsQueryResource.class);
    }
}