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

package stroom.search.solr;

import org.apache.lucene.search.Query;
import org.apache.solr.client.solrj.SolrServerException;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.meta.shared.ExpressionUtil;
import stroom.search.solr.search.SearchExpressionQueryBuilder;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.search.SolrSearchConfig;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Singleton
public class SolrIndexRetentionExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexRetentionExecutor.class);

    private static final String TASK_NAME = "Solr Index Retention Executor";
    private static final String LOCK_NAME = "SolrIndexRetentionExecutor";
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final SolrIndexStore solrIndexStore;
    private final SolrIndexCache solrIndexCache;
    private final SolrIndexClientCache solrIndexClientCache;
    private final WordListProvider dictionaryStore;
    private final ClusterLockService clusterLockService;
    private final TaskContext taskContext;
    private final SolrSearchConfig searchConfig;

    @Inject
    public SolrIndexRetentionExecutor(final SolrIndexStore solrIndexStore,
                                      final SolrIndexCache solrIndexCache,
                                      final SolrIndexClientCache solrIndexClientCache,
                                      final WordListProvider dictionaryStore,
                                      final ClusterLockService clusterLockService,
                                      final TaskContext taskContext,
                                      final SolrSearchConfig searchConfig) {
        this.solrIndexStore = solrIndexStore;
        this.solrIndexCache = solrIndexCache;
        this.solrIndexClientCache = solrIndexClientCache;
        this.dictionaryStore = dictionaryStore;
        this.clusterLockService = clusterLockService;
        this.taskContext = taskContext;
        this.searchConfig = searchConfig;
    }

    public void exec() {
        taskContext.setName(TASK_NAME);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info("Start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final List<DocRef> docRefs = solrIndexStore.list();
                    if (docRefs != null) {
                        docRefs.forEach(this::performRetention);
                    }
                    info("Finished in " + logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    private void performRetention(final DocRef docRef) {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                final CachedSolrIndex cachedSolrIndex = solrIndexCache.get(docRef);
                if (cachedSolrIndex != null) {
                    final SolrIndexDoc solrIndexDoc = cachedSolrIndex.getIndex();
                    final int termCount = ExpressionUtil.terms(solrIndexDoc.getRetentionExpression(), null).size();
                    if (termCount > 0) {
                        final Map<String, SolrIndexField> indexFieldsMap = cachedSolrIndex.getFieldsMap();
                        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                                dictionaryStore,
                                indexFieldsMap,
                                searchConfig.getMaxBooleanClauseCount(),
                                null,
                                System.currentTimeMillis());
                        final SearchExpressionQuery searchExpressionQuery = searchExpressionQueryBuilder.buildQuery(solrIndexDoc.getRetentionExpression());
                        final Query query = searchExpressionQuery.getQuery();
                        final String queryString = query.toString();
                        solrIndexClientCache.context(solrIndexDoc.getSolrConnectionConfig(), solrClient -> {
                            try {
                                info("Deleting data from '" + solrIndexDoc.getName() + "' matching query '" + queryString + "'");
                                solrClient.deleteByQuery(solrIndexDoc.getCollection(), queryString, 10000);
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

    private void info(final String message) {
        taskContext.info(message);
        LOGGER.info(() -> message);
    }
}
