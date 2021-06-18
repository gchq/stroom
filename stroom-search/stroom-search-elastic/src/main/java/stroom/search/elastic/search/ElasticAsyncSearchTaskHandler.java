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

package stroom.search.elastic.search;

import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.search.elastic.ElasticIndexCache;
import stroom.search.elastic.ElasticIndexService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;

import java.util.List;
import javax.inject.Inject;

public class ElasticAsyncSearchTaskHandler {
    private final ElasticIndexCache elasticIndexCache;
    private final SecurityContext securityContext;
    private final ElasticIndexService elasticIndexService;
    private final ElasticClusterSearchTaskHandler clusterSearchTaskHandler;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;

    @Inject
    ElasticAsyncSearchTaskHandler(final ElasticIndexCache elasticIndexCache,
                                  final ElasticIndexService elasticIndexService,
                                  final SecurityContext securityContext,
                                  final ElasticClusterSearchTaskHandler clusterSearchTaskHandler,
                                  final TaskManager taskManager,
                                  final ClusterTaskTerminator clusterTaskTerminator) {
        this.elasticIndexCache = elasticIndexCache;
        this.elasticIndexService = elasticIndexService;
        this.securityContext = securityContext;
        this.clusterSearchTaskHandler = clusterSearchTaskHandler;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
    }

    public void exec(final TaskContext taskContext,
                     final ElasticAsyncSearchTask task,
                     final Coprocessors coprocessors,
                     final ElasticSearchResultCollector resultCollector
    ) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    taskContext.info(() -> task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();
                    final ElasticIndexDoc index = elasticIndexCache.get(query.getDataSource());
                    final List<String> storedFields = elasticIndexService.getStoredFields(index);

                    clusterSearchTaskHandler.exec(taskContext,
                        task,
                        index,
                        query,
                        storedFields.toArray(new String[0]),
                        task.getNow(),
                        task.getDateTimeSettings(),
                        coprocessors
                    );

                    // Await completion
                    taskContext.info(() -> task.getSearchName() + " - searching");
                    resultCollector.awaitCompletion();

                } catch (final RuntimeException e) {
                    coprocessors.getErrorConsumer().accept(e);
                } catch (final InterruptedException e) {
                    coprocessors.getErrorConsumer().accept(e);

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                } finally {
                    taskContext.info(() -> task.getSearchName() + " - complete");

                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    terminateTasks(task, taskContext.getTaskId());

                    // Ensure search is complete even if we had errors.
                    resultCollector.complete();

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    taskContext.info(() -> task.getSearchName() + " - staying alive for UI requests");
                }
            }
        }));
    }

    private void terminateTasks(final ElasticAsyncSearchTask task, final TaskId taskId) {
        // Terminate this task.
        taskManager.terminate(taskId);

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        clusterTaskTerminator.terminate(task.getSearchName(), taskId, "ElasticAsyncSearchTask");
    }
}
