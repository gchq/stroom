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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.solr.search.SearchExpressionQueryBuilder;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.search.SolrSearchConfig;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.lucene553.search.Query;
import org.apache.solr.client.solrj.SolrServerException;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

@Singleton
public class SolrIndexRetentionExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexRetentionExecutor.class);

    private static final String LOCK_NAME = "SolrIndexRetentionExecutor";
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final SolrIndexStore solrIndexStore;
    private final SolrIndexDocCache solrIndexDocCache;
    private final SolrIndexClientCache solrIndexClientCache;
    private final WordListProvider dictionaryStore;
    private final ClusterLockService clusterLockService;
    private final Provider<SolrSearchConfig> searchConfigProvider;
    private final TaskContextFactory taskContextFactory;
    private final IndexFieldCache indexFieldCache;

    @Inject
    public SolrIndexRetentionExecutor(final SolrIndexStore solrIndexStore,
                                      final SolrIndexDocCache solrIndexDocCache,
                                      final SolrIndexClientCache solrIndexClientCache,
                                      final WordListProvider dictionaryStore,
                                      final ClusterLockService clusterLockService,
                                      final Provider<SolrSearchConfig> searchConfigProvider,
                                      final TaskContextFactory taskContextFactory,
                                      final IndexFieldCache indexFieldCache) {
        this.solrIndexStore = solrIndexStore;
        this.solrIndexDocCache = solrIndexDocCache;
        this.solrIndexClientCache = solrIndexClientCache;
        this.dictionaryStore = dictionaryStore;
        this.clusterLockService = clusterLockService;
        this.searchConfigProvider = searchConfigProvider;
        this.taskContextFactory = taskContextFactory;
        this.indexFieldCache = indexFieldCache;
    }

    public void exec() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final List<DocRef> docRefs = solrIndexStore.list();
                    if (docRefs != null) {
                        docRefs.forEach(this::performRetention);
                    }
                    info(() -> "Finished in " + logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    private void performRetention(final DocRef docRef) {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                final SolrIndexDoc index = solrIndexDocCache.get(docRef);
                if (index != null) {
                    final int termCount = ExpressionUtil.terms(index.getRetentionExpression(), null)
                            .size();
                    if (termCount > 0) {
                        final SearchExpressionQueryBuilder searchExpressionQueryBuilder =
                                new SearchExpressionQueryBuilder(
                                        docRef,
                                        indexFieldCache,
                                        dictionaryStore,
                                        searchConfigProvider.get().getMaxBooleanClauseCount(),
                                        DateTimeSettings.builder().build());
                        final SearchExpressionQuery searchExpressionQuery = searchExpressionQueryBuilder
                                .buildQuery(index.getRetentionExpression());
                        final Query query = searchExpressionQuery.getQuery();
                        final String queryString = query.toString();
                        solrIndexClientCache.context(index.getSolrConnectionConfig(), solrClient -> {
                            try {
                                info(() ->
                                        "Deleting data from '" + index.getName()
                                                + "' matching query '" + queryString + "'");
                                solrClient.deleteByQuery(
                                        index.getCollection(),
                                        queryString,
                                        10000);
                            } catch (final SolrServerException | IOException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        });
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    private void info(final Supplier<String> message) {
        taskContextFactory.current().info(message);
        LOGGER.info(message);
    }
}
