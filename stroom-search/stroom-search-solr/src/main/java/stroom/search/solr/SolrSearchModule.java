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

package stroom.search.solr;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.job.api.ScheduledJobsBinder;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.search.solr.indexing.SolrIndexingElementModule;
import stroom.search.solr.search.SolrSearchProvider;
import stroom.search.solr.search.StroomSolrIndexQueryResourceImpl;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class SolrSearchModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SolrIndexingElementModule());

        bind(SolrIndexDocCache.class).to(SolrIndexDocCacheImpl.class);
        bind(SolrIndexClientCache.class).to(SolrIndexClientCacheImpl.class);
        bind(SolrIndexStore.class).to(SolrIndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(SolrSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(SolrSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), IndexFieldProvider.class)
                .addBinding(SolrSearchProvider.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(SolrIndexDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(SolrIndexDocCacheImpl.class)
                .addBinding(SolrIndexClientCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(SolrIndexStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(SolrIndexStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(SolrIndexStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(SolrIndexDoc.TYPE, SolrIndexStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(SolrIndexResourceImpl.class)
                .bind(StroomSolrIndexQueryResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataRetention.class, builder -> builder
                        .name("Solr Index Retention")
                        .description("Logically delete indexed documents in Solr indexes based on the specified " +
                                "deletion query")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_2AM.getExpression()))
                .bindJobTo(SolrIndexOptimiseExecutorJob.class, builder -> builder
                        .name("Solr Index Optimise")
                        .description("Optimise Solr indexes")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_3AM.getExpression()));
    }

    private static class DataRetention extends RunnableWrapper {

        @Inject
        DataRetention(final SolrIndexRetentionExecutor dataRetentionExecutor) {
            super(dataRetentionExecutor::exec);
        }
    }

    private static class SolrIndexOptimiseExecutorJob extends RunnableWrapper {

        @Inject
        SolrIndexOptimiseExecutorJob(final SolrIndexOptimiseExecutor executor) {
            super(executor::exec);
        }
    }
}
