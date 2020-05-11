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

import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.statistics.impl.sql.StatisticsQueryService;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class SQLStatisticSearchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StatisticsQueryService.class).to(StatisticsQueryServiceImpl.class);
        bind(StatisticsSearchService.class).to(StatisticsSearchServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(SqlStatisticsSearchResponseCreatorManager.class);

        RestResourcesBinder.create(binder())
                .bind(SqlStatisticsQueryResource.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(EvictExpiredElements.class, builder -> builder
                        .withName("Evict expired elements")
                        .withSchedule(PERIODIC, "10s")
                        .withManagedState(false));
    }

    private static class EvictExpiredElements extends RunnableWrapper {
        @Inject
        EvictExpiredElements(final SqlStatisticsSearchResponseCreatorManager sqlStatisticsSearchResponseCreatorManager) {
            super(sqlStatisticsSearchResponseCreatorManager::evictExpiredElements);
        }
    }
}