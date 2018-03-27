/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.server.common.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.mapreduce.BlockingPairQueue;
import stroom.mapreduce.PairQueue;
import stroom.mapreduce.UnsafePairQueue;
import stroom.query.CompiledDepths;
import stroom.query.CompiledFields;
import stroom.query.Item;
import stroom.query.ItemMapper;
import stroom.query.ItemPartitioner;
import stroom.query.Payload;
import stroom.query.TableCoprocessorSettings;
import stroom.query.TablePayload;
import stroom.query.shared.TableSettings;
import stroom.security.SecurityContext;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("unused") // Instantiated by TaskManager
@TaskHandlerBean(task = StatStoreSearchTask.class)
@Scope(value = StroomScope.TASK)
public class StatStoreSearchTaskHandler extends AbstractTaskHandler<StatStoreSearchTask, VoidResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatStoreSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StatStoreSearchTaskHandler.class);

    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;


    private final TaskMonitor taskMonitor;
    private final SecurityContext securityContext;
    private final StatisticsSearchService statisticsSearchService;

    @SuppressWarnings("unused") // Called by DI
    @Inject
    StatStoreSearchTaskHandler(final TaskMonitor taskMonitor,
                               final SecurityContext securityContext,
                               final StatisticsSearchService statisticsSearchService) {
        this.taskMonitor = taskMonitor;
        this.securityContext = securityContext;
        this.statisticsSearchService = statisticsSearchService;
    }

    @Override
    public VoidResult exec(final StatStoreSearchTask task) {
        try {
            securityContext.elevatePermissions();

            final StatStoreSearchResultCollector resultCollector = task.getResultCollector();

            if (!task.isTerminated()) {
                taskMonitor.info(task.getSearchName() + " - initialising");

                final StatisticStoreEntity entity = task.getEntity();

                Preconditions.checkNotNull(entity);

                //fieldIndexMap is common across all coprocessors as we will have a single String[] that will
                //be returned from the query and used by all coprocessors. The map is populated by the expression
                //parsing on each coprocessor
                final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);

                //each coprocessor has its own settings and field requirements
                final List<StatStoreTableCoprocessor> tableCoprocessors = task.getCoprocessorMap().entrySet().stream()
                        .map(entry ->
                                new StatStoreTableCoprocessor(
                                        entry.getKey(),
                                        (TableCoprocessorSettings) entry.getValue(),
                                        fieldIndexMap,
                                        task.getMonitor(),
                                        task.getSearch().getParamMap()))
                        .collect(ImmutableList.toImmutableList());

                // convert the search into something stats understands
                final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(
                        task.getSearch(),
                        entity);

                final AtomicLong counter = new AtomicLong(0);
                taskMonitor.info(task.getSearchName() + " - executing database query");

                // subscribe to the flowable, mapping each resultSet to a String[]
                // After the window period has elapsed a new flowable is create for those rows received 
                // in that window, which can all be processed and sent
                // If the task is canceled, the flowable produced by search() will stop emitting
                // Set up the results flowable, the search wont be executed until subscribe is called
                statisticsSearchService.search(entity, criteria, fieldIndexMap)
                        .window(PROCESS_PAYLOAD_INTERVAL_SECS, TimeUnit.SECONDS)
                        .subscribe(
                                windowedFlowable -> {
                                    LOGGER.trace("onNext called for outer flowable");
                                    windowedFlowable.subscribe(
                                            data -> {
                                                LAMBDA_LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(data)));
                                                counter.incrementAndGet();

                                                // give the data array to each of our coprocessors
                                                tableCoprocessors.forEach(tableCoprocessor ->
                                                        tableCoprocessor.receive(data));
                                            },
                                            throwable -> {
                                                throw new RuntimeException(String.format("Error in flow, %s",
                                                        throwable.getMessage()), throwable);
                                            },
                                            () -> {
                                                LAMBDA_LOGGER.debug(() ->
                                                        String.format("onComplete of inner flowable called, processing results so far, counter: %s",
                                                                counter.get()));
                                                //completed our timed window so create and pass on a payload for the
                                                //data we have gathered so far
                                                processPayloads(resultCollector, tableCoprocessors);
                                                taskMonitor.info(task.getSearchName() +
                                                        " - running database query (" + counter.get() + " rows fetched)");
                                            });
                                },
                                throwable -> {
                                    throw new RuntimeException(String.format("Error in flow, %s",
                                            throwable.getMessage()), throwable);
                                },
                                () -> LOGGER.debug("onComplete of outer flowable called"));

                LOGGER.debug("Out of flowable");

                //flows all complete, so process any remaining data
                processPayloads(resultCollector, tableCoprocessors);
                taskMonitor.info(task.getSearchName() +
                        " - completed database query (" + counter.get() + " rows fetched)");
            }

            // Let the result handler know search has finished.
            resultCollector.getResultHandler().setComplete(true);

            taskMonitor.info(task.getSearchName() + " - complete");

            return VoidResult.INSTANCE;

        } finally {
            securityContext.restorePermissions();
        }
    }

    /**
     * Synchronized to ensure multiple threads don't fight over the coprocessors which is unlikely to
     * happen anyway as it is mostly used in
     */
    private synchronized void processPayloads(final StatStoreSearchResultCollector resultCollector,
                                              final List<StatStoreTableCoprocessor> tableCoprocessors) {

        LOGGER.debug("processPayloads called for {} coprocessors", tableCoprocessors.size());

        //build a payload map from whatever the coprocessors have in them, if anything
        final Map<Integer, Payload> payloadMap = tableCoprocessors.stream()
                .map(tableCoprocessor ->
                        Maps.immutableEntry(tableCoprocessor.getId(), tableCoprocessor.createPayload()))
                .filter(entry ->
                        entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // give the processed results to the collector, it will handle nulls
        resultCollector.handle(payloadMap);
    }

    private Consumer<String[]> buildDataArrayConsumer(
            final StatStoreSearchTask task,
            final Map<Integer, Payload> payloadMap,
            final FieldIndexMap fieldIndexMap,
            final Integer id,
            final TableCoprocessorSettings coprocessorSettings) {

        final TableSettings tableSettings = coprocessorSettings.getTableSettings();

        final CompiledDepths compiledDepths = new CompiledDepths(
                tableSettings.getFields(),
                tableSettings.showDetail());

        final CompiledFields compiledFields = new CompiledFields(
                tableSettings.getFields(),
                fieldIndexMap, task.getSearch().getParamMap());

        // Create a queue of string arrays.
        final PairQueue<String, Item> queue = new BlockingPairQueue<>(taskMonitor);
        final ItemMapper mapper = new ItemMapper(
                queue,
                compiledFields,
                compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        //create a consumer of the data array that will ultimately be returned from the database query
        return (String[] data) -> {
            mapper.collect(null, data);

            // partition and reduce based on table settings.
            final UnsafePairQueue<String, Item> outputQueue = new UnsafePairQueue<>();

            // Create a partitioner to perform result reduction if needed.
            final ItemPartitioner partitioner = new ItemPartitioner(compiledDepths.getDepths(),
                    compiledDepths.getMaxDepth());
            partitioner.setOutputCollector(outputQueue);

            // Partition the data prior to forwarding to the target node.
            partitioner.read(queue);

            // Perform partitioning.
            partitioner.partition();

            final Payload payload = new TablePayload(outputQueue);
            payloadMap.put(id, payload);
        };
    }


}
