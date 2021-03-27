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

import stroom.dictionary.server.DictionaryStore;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.query.api.v2.DocRef;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.streamtask.shared.ExpressionUtil;
import stroom.task.server.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = StroomScope.TASK)
public class ElasticIndexRetentionExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexRetentionExecutor.class);

    private static final String TASK_NAME = "Elastic Index Retention Executor";
    private static final String LOCK_NAME = "ElasticIndexRetentionExecutor";
    private static final int DELETE_REQUEST_TIMEOUT_MILLIS = 10000;

    private final ElasticClusterStore elasticClusterStore;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticIndexCache elasticIndexCache;
    private final ElasticIndexService elasticIndexService;
    private final ElasticClientCache elasticClientCache;
    private final DictionaryStore dictionaryStore;
    private final ClusterLockService clusterLockService;
    private final TaskContext taskContext;

    @Inject
    public ElasticIndexRetentionExecutor(
        final ElasticClusterStore elasticClusterStore,
        final ElasticIndexStore elasticIndexStore,
        final ElasticIndexCache elasticIndexCache,
        final ElasticIndexService elasticIndexService,
        final ElasticClientCache elasticClientCache,
        final DictionaryStore dictionaryStore,
        final ClusterLockService clusterLockService,
        final TaskContext taskContext
    ) {
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticIndexCache = elasticIndexCache;
        this.elasticIndexService = elasticIndexService;
        this.elasticClientCache = elasticClientCache;
        this.dictionaryStore = dictionaryStore;
        this.clusterLockService = clusterLockService;
        this.taskContext = taskContext;
    }

    @StroomSimpleCronSchedule(cron = "0 2 *")
    @JobTrackedSchedule(jobName = "Elastic Index Retention", description = "Logically delete indexed documents in Elasticsearch indices based on the specified deletion query")
    public void exec() {
        taskContext.setName(TASK_NAME);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info("Start");
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                if (!taskContext.isTerminated()) {
                    final List<DocRef> docRefs = elasticIndexStore.list();
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
                final ElasticIndex elasticIndex = elasticIndexCache.get(docRef);
                if (elasticIndex != null) {
                    final int termCount = ExpressionUtil.terms(elasticIndex.getRetentionExpression(), null).size();
                    if (termCount > 0) {
                        final Map<String, ElasticIndexField> indexFieldsMap = getFieldsMap(docRef);
                        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                                dictionaryStore,
                                indexFieldsMap,
                                null,
                                System.currentTimeMillis());

                        final QueryBuilder query = searchExpressionQueryBuilder.buildQuery(elasticIndex.getRetentionExpression());
                        final ElasticCluster elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());

                        elasticClientCache.context(elasticCluster.getConnectionConfig(), elasticClient -> {
                            try {
                                info("Deleting data from Elasticsearch index '" + elasticIndex.getName() + "'");
                                DeleteByQueryRequest request = new DeleteByQueryRequest(elasticIndex.getIndexName());
                                request.setQuery(query);
                                request.setTimeout(new TimeValue(DELETE_REQUEST_TIMEOUT_MILLIS));
                                request.setRefresh(true);
                                elasticClient.deleteByQuery(request, RequestOptions.DEFAULT);
                            } catch (Exception e) {
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

    /**
     * Query field mappings for this index
     */
    private Map<String, ElasticIndexField> getFieldsMap(final DocRef docRef) {
        final ElasticIndex index = elasticIndexStore.read(docRef.getUuid());
        if (index == null) {
            throw new RuntimeException("Elasticsearch index not found for: '" + docRef.getUuid() + "'");
        }

        return elasticIndexService.getFieldsMap(index);
    }

    private void info(final String message) {
        taskContext.info(message);
        LOGGER.info(() -> message);
    }
}
