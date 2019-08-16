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

package stroom.search.solr.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.ResultHandler;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.SolrIndexCache;
import stroom.search.solr.shared.SolrIndexField;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TaskHandlerBean(task = SolrAsyncSearchTask.class)
@Scope(value = StroomScope.TASK)
public class SolrAsyncSearchTaskHandler extends AbstractTaskHandler<SolrAsyncSearchTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrAsyncSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SolrAsyncSearchTaskHandler.class);


    private final TaskMonitor taskMonitor;
    private final SolrIndexCache solrIndexCache;
    private final SecurityContext securityContext;
    private final SolrClusterSearchTaskHandler clusterSearchTaskHandler;

    @Inject
    SolrAsyncSearchTaskHandler(final TaskMonitor taskMonitor,
                               final SolrIndexCache solrIndexCache,
                               final SecurityContext securityContext,
                               final SolrClusterSearchTaskHandler clusterSearchTaskHandler) {
        this.taskMonitor = taskMonitor;
        this.solrIndexCache = solrIndexCache;
        this.securityContext = securityContext;
        this.clusterSearchTaskHandler = clusterSearchTaskHandler;
    }

    @Override
    public VoidResult exec(final SolrAsyncSearchTask task) {
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            final SolrSearchResultCollector resultCollector = task.getResultCollector();
            final ResultHandler resultHandler = resultCollector.getResultHandler();

            if (!task.isTerminated()) {
                try {
                    taskMonitor.info(task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    // Reload the index.
                    final CachedSolrIndex index = solrIndexCache.get(query.getDataSource());

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    // TODO : Specify stored fields based on the fields that all
                    // coprocessors will require. Also
                    // batch search only needs stream and event id stored fields.
                    final String[] storedFields = getStoredFields(index);

                    final SolrClusterSearchTask clusterSearchTask = new SolrClusterSearchTask(index, query, task.getResultSendFrequency(), storedFields,
                            task.getCoprocessorMap(), task.getDateTimeLocale(), task.getNow());
                    clusterSearchTaskHandler.exec(clusterSearchTask, resultCollector);

                    taskMonitor.info(task.getSearchName() + " - searching...");

                    final CountDownLatch completionCountDownLatch = new CountDownLatch(1);

                    final Runnable collectorChangeListener = () -> {
                        // something has changed so recheck all the states to see if we can release
                        // the latch to release any waiters
                        if (task.isTerminated() ||
                                resultHandler.shouldTerminateSearch() ||
                                resultHandler.isComplete()) {
                            LOGGER.trace("Change listener counting down completion latch");
                            completionCountDownLatch.countDown();
                        } else if (resultCollector.isComplete()) {
                            LOGGER.trace("Change listener setting SearchResultHandler to complete");
                            // all the expected nodes have provided results
                            resultHandler.setComplete(true);
                        } else {
                            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Change listener condition not met, " +
                                            "isTerminated={}, shouldTerminatSearch={}, isComplete={}",
                                    task.isTerminated(),
                                    resultHandler.shouldTerminateSearch(),
                                    resultHandler.isComplete()));
                        }
                    };

                    //if it has completed or something has changed on the resultCollector then
                    //test the conditions, else sleep

                    LOGGER.trace("Registering listeners");

                    resultHandler.registerCompletionListener(() -> {
                        LOGGER.trace("Counting down completionCountDownLatch");
                        completionCountDownLatch.countDown();
                    });
                    resultCollector.registerChangeListner(collectorChangeListener);

                    while (!task.isTerminated() &&
                            !resultHandler.shouldTerminateSearch() &&
                            !resultHandler.isComplete()) {

                        boolean awaitResult = LAMBDA_LOGGER.logDurationIfTraceEnabled(
                                () -> {
                                    try {
                                        // block and wait for up to 10s for our search to be completed/terminated
                                        return completionCountDownLatch.await(10, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        //Don't reset the interrupt status as we are at the top level of
                                        //the task execution
                                        throw new RuntimeException("Thread interrupted");
                                    }
                                },
                                "waiting for completion condition");

                        LOGGER.trace("await finished with result {}", awaitResult);
                    }
                    taskMonitor.info(task.getSearchName() + " - complete");

                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    if (task.isTerminated() || resultHandler.shouldTerminateSearch()) {
                        terminateTasks(task);
                    }
                } catch (final Exception e) {
                    resultCollector.getErrorSet().add(e.getMessage());
                }

                // Let the result handler know search has finished.
                resultHandler.setComplete(true);

                // We need to wait here for the client to keep getting results if
                // this is an interactive search.
                taskMonitor.info(task.getSearchName() + " - staying alive for UI requests");
            }

            return VoidResult.INSTANCE;
        }
    }

    private void terminateTasks(final SolrAsyncSearchTask task) {
        // Terminate this task.
        task.terminate();
    }

    private String[] getStoredFields(final CachedSolrIndex index) {
        return index.getFields()
                .stream()
                .filter(SolrIndexField::isStored)
                .map(SolrIndexField::getFieldName)
                .toArray(String[]::new);
    }
}
