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

import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.query.api.v2.DocRef;
import stroom.search.solr.shared.SolrIndex;
import stroom.task.server.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

@Component
@Scope(value = StroomScope.TASK)
public class SolrIndexOptimiseExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexOptimiseExecutor.class);

    private static final String TASK_NAME = "Solr Index Optimise Executor";
    private static final String LOCK_NAME = "SolrIndexOptimiseExecutor";

    private final SolrIndexStore solrIndexStore;
    private final SolrIndexClientCache solrIndexClientCache;
    private final ClusterLockService clusterLockService;
    private final TaskContext taskContext;

    @Inject
    public SolrIndexOptimiseExecutor(final SolrIndexStore solrIndexStore,
                                     final SolrIndexClientCache solrIndexClientCache,
                                     final ClusterLockService clusterLockService,
                                     final TaskContext taskContext) {
        this.solrIndexStore = solrIndexStore;
        this.solrIndexClientCache = solrIndexClientCache;
        this.clusterLockService = clusterLockService;
        this.taskContext = taskContext;
    }

    @StroomSimpleCronSchedule(cron = "0 3 *")
    @JobTrackedSchedule(jobName = "Solr Index Optimise", description = "Optimise Solr indexes")
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
                final SolrIndex solrIndex = solrIndexStore.read(docRef.getUuid());
                if (solrIndex != null) {
                    solrIndexClientCache.context(solrIndex.getSolrConnectionConfig(), solrClient -> {
                        try {
                            info("Optimising '" + solrIndex.getName() + "'");
                            solrClient.optimize(solrIndex.getCollection());
                        } catch (final SolrServerException | IOException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    });
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
