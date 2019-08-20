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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.dictionary.server.DictionaryStore;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.query.api.v2.DocRef;
import stroom.search.solr.search.SearchExpressionQueryBuilder;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndex;
import stroom.search.solr.shared.SolrIndexField;
import stroom.streamtask.shared.ExpressionUtil;
import stroom.task.server.TaskContext;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = StroomScope.TASK)
public class SolrIndexRetentionExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexRetentionExecutor.class);

    private static final String TASK_NAME = "Solr Index Retention Executor";
    private static final String LOCK_NAME = "SolrIndexRetentionExecutor";
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final SolrIndexStore solrIndexStore;
    private final SolrIndexCache solrIndexCache;
    private final SolrIndexClientCache solrIndexClientCache;
    private final DictionaryStore dictionaryStore;
    private final ClusterLockService clusterLockService;
    private final TaskContext taskContext;
    private final int maxBooleanClauseCount;

    @Inject
    public SolrIndexRetentionExecutor(final SolrIndexStore solrIndexStore,
                                      final SolrIndexCache solrIndexCache,
                                      final SolrIndexClientCache solrIndexClientCache,
                                      final DictionaryStore dictionaryStore,
                                      final ClusterLockService clusterLockService,
                                      final TaskContext taskContext,
                                      @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount) {
        this.solrIndexStore = solrIndexStore;
        this.solrIndexCache = solrIndexCache;
        this.solrIndexClientCache = solrIndexClientCache;
        this.dictionaryStore = dictionaryStore;
        this.clusterLockService = clusterLockService;
        this.taskContext = taskContext;
        this.maxBooleanClauseCount = PropertyUtil.toInt(maxBooleanClauseCount, DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT);
    }

    @StroomSimpleCronSchedule(cron = "0 2 *")
    @JobTrackedSchedule(jobName = "Solr Index Retention", description = "Logically delete indexed documents in Solr indexes based on the specified deletion query")
    public void exec() {
        taskContext.setName(TASK_NAME);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info("Start");
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                if (!taskContext.isTerminated()) {
                    final List<DocRef> docRefs = solrIndexStore.list();
                    if (docRefs != null) {
                        docRefs.forEach(this::performRetention);
                    }
                    info("Finished in " + logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        } else {
            info("Skipped as did not get lock in " + logExecutionTime);
        }
    }

    private void performRetention(final DocRef docRef) {
        if (!taskContext.isTerminated()) {
            try {
                final CachedSolrIndex cachedSolrIndex = solrIndexCache.get(docRef);
                if (cachedSolrIndex != null) {
                    final SolrIndex solrIndex = cachedSolrIndex.getIndex();
                    final int termCount = ExpressionUtil.terms(solrIndex.getRetentionExpression(), null).size();
                    if (termCount > 0) {
                        final Map<String, SolrIndexField> indexFieldsMap = cachedSolrIndex.getFieldsMap();
                        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                                dictionaryStore,
                                indexFieldsMap,
                                maxBooleanClauseCount,
                                null,
                                System.currentTimeMillis());
                        final SearchExpressionQuery searchExpressionQuery = searchExpressionQueryBuilder.buildQuery(solrIndex.getRetentionExpression());
                        final Query query = searchExpressionQuery.getQuery();
                        final String queryString = query.toString();
                        solrIndexClientCache.context(solrIndex.getSolrConnectionConfig(), solrClient -> {
                            try {
                                info("Deleting data from '" + solrIndex.getName() + "' matching query '" + queryString + "'");
                                solrClient.deleteByQuery(solrIndex.getCollection(), queryString, 10000);
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
