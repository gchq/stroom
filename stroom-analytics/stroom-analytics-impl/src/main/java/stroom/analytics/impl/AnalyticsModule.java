/*
 * Copyright 2020 Crown Copyright
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

package stroom.analytics.impl;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.explorer.api.HasDataSourceDocRefs;
import stroom.job.api.ScheduledJobsBinder;
import stroom.query.common.v2.HasResultStoreInfo;
import stroom.query.common.v2.SearchProvider;
import stroom.search.impl.NodeSearchTaskHandlerProvider;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class AnalyticsModule extends AbstractModule {

    @Override
    protected void configure() {
        ScheduledJobsBinder.create(binder())
                .bindJobTo(TableBuilderAnalyticsExecutorRunnable.class, builder -> builder
                        .name("Analytics Executor: Table Builder")
                        .description("Run table building analytics periodically")
                        .schedule(PERIODIC, "10m")
                        .enabled(false)
                        .advanced(true))
                .bindJobTo(StreamingAnalyticsExecutorRunnable.class, builder -> builder
                        .name("Analytics Executor: Streaming")
                        .description("Run streaming analytics periodically")
                        .schedule(PERIODIC, "1m")
                        .enabled(false)
                        .advanced(true))
                .bindJobTo(ScheduledAnalyticsExecutorRunnable.class, builder -> builder
                        .name("Analytics Executor: Scheduled Query")
                        .description("Run scheduled index query analytics periodically")
                        .schedule(PERIODIC, "10m")
                        .enabled(false)
                        .advanced(true));
        GuiceUtil.buildMultiBinder(binder(), HasResultStoreInfo.class).addBinding(AnalyticDataStores.class);

        RestResourcesBinder.create(binder())
                .bind(AnalyticProcessResourceImpl.class)
                .bind(AnalyticDataShardResourceImpl.class);

        // Live federated search provision.
        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(AnalyticsSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(AnalyticsSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), HasDataSourceDocRefs.class)
                .addBinding(AnalyticsSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), NodeSearchTaskHandlerProvider.class)
                .addBinding(AnalyticsNodeSearchTaskHandlerProvider.class);
    }

    private static class TableBuilderAnalyticsExecutorRunnable extends RunnableWrapper {

        @Inject
        TableBuilderAnalyticsExecutorRunnable(final TableBuilderAnalyticsExecutor executor) {
            super(executor::exec);
        }
    }

    private static class StreamingAnalyticsExecutorRunnable extends RunnableWrapper {

        @Inject
        StreamingAnalyticsExecutorRunnable(final StreamingAnalyticsExecutor executor) {
            super(executor::exec);
        }
    }

    private static class ScheduledAnalyticsExecutorRunnable extends RunnableWrapper {

        @Inject
        ScheduledAnalyticsExecutorRunnable(final ScheduledQueryAnalyticExecutor executor) {
            super(executor::exec);
        }
    }
}

