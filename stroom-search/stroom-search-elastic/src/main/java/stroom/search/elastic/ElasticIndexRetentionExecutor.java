/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search.elastic;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;

import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.Supplier;

@Singleton
public class ElasticIndexRetentionExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexRetentionExecutor.class);

    private static final String TASK_NAME = "Elastic Index Retention Executor";
    private static final String LOCK_NAME = "ElasticIndexRetentionExecutor";
    private static final int DELETE_REQUEST_TIMEOUT_SECONDS = 60;

    private final Provider<ElasticConfig> elasticConfigProvider;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticIndexCache elasticIndexCache;
    private final ElasticClientCache elasticClientCache;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider dictionaryStore;
    private final ClusterLockService clusterLockService;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public ElasticIndexRetentionExecutor(
            final Provider<ElasticConfig> elasticConfigProvider,
            final ElasticClusterStore elasticClusterStore,
            final ElasticIndexStore elasticIndexStore,
            final ElasticIndexCache elasticIndexCache,
            final ElasticClientCache elasticClientCache,
            final IndexFieldCache indexFieldCache,
            final WordListProvider dictionaryStore,
            final ClusterLockService clusterLockService,
            final TaskContextFactory taskContextFactory) {
        this.elasticConfigProvider = elasticConfigProvider;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticIndexCache = elasticIndexCache;
        this.elasticClientCache = elasticClientCache;
        this.indexFieldCache = indexFieldCache;
        this.dictionaryStore = dictionaryStore;
        this.clusterLockService = clusterLockService;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        taskContextFactory.context(TASK_NAME, this::exec).run();
    }

    private void exec(final TaskContext taskContext) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        info(taskContext, () -> "Start");

        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final List<DocRef> docRefs = elasticIndexStore.list();

                    if (docRefs != null) {
                        docRefs.forEach(docRef -> performRetention(taskContext, docRef));
                    }

                    info(taskContext, () -> "Finished in " + logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    private void performRetention(final TaskContext taskContext, final DocRef docRef) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            final ElasticIndexDoc elasticIndex = elasticIndexCache.get(docRef);

            if (elasticIndex != null) {
                final int termCount = ExpressionUtil.terms(elasticIndex.getRetentionExpression(), null).size();
                if (termCount > 0) {
                    final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                            docRef,
                            indexFieldCache,
                            dictionaryStore,
                            DateTimeSettings.builder().build());

                    final Query query = searchExpressionQueryBuilder.buildQuery(
                            elasticIndex.getRetentionExpression());
                    final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(
                            elasticIndex.getClusterRef());

                    elasticClientCache.context(elasticCluster.getConnection(), elasticClient -> {
                        try {
                            info(taskContext, () ->
                                    "Deleting data from Elasticsearch index '" + elasticIndex.getName() + "'");

                            final DeleteByQueryRequest request = DeleteByQueryRequest.of(r -> r
                                    .index(elasticIndex.getIndexName())
                                    .query(query)
                                    .timeout(Time.of(t -> t.time(String.format("%ds", DELETE_REQUEST_TIMEOUT_SECONDS))))
                                    .refresh(true)
                                    .scrollSize(elasticConfigProvider.get().getRetentionConfig().getScrollSize())
                            );

                            elasticClient.deleteByQuery(request);
                        } catch (final Exception e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    });
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void info(final TaskContext taskContext, final Supplier<String> message) {
        taskContext.info(message);
        LOGGER.info(message);
    }
}
