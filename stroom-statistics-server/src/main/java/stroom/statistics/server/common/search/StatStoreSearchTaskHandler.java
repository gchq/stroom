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
import org.springframework.stereotype.Component;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
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
import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.Search;
import stroom.query.shared.TableSettings;
import stroom.security.SecurityContext;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("unused") // Instantiated by TaskManager
@Component
@Scope(value = StroomScope.PROTOTYPE)
public class StatStoreSearchTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatStoreSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StatStoreSearchTaskHandler.class);

    private static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;

    private final TaskContext taskContext;
    private final SecurityContext securityContext;
    private final StatisticsSearchService statisticsSearchService;

    @SuppressWarnings("unused") // Called by DI
    @Inject
    StatStoreSearchTaskHandler(final TaskContext taskContext,
                               final SecurityContext securityContext,
                               final StatisticsSearchService statisticsSearchService) {
        this.taskContext = taskContext;
        this.securityContext = securityContext;
        this.statisticsSearchService = statisticsSearchService;
    }

    public void exec(final String searchName,
                     final Search search,
                     final StatisticStoreEntity entity,
                     final Map<Integer, CoprocessorSettings> coprocessorMap,
                     final StatStoreSearchResultCollector resultCollector) {
        try {
            securityContext.elevatePermissions();

            if (!taskContext.isTerminated()) {
                taskContext.info(searchName + " - initialising");

                Preconditions.checkNotNull(entity);

                //fieldIndexMap is common across all coprocessors as we will have a single String[] that will
                //be returned from the query and used by all coprocessors. The map is populated by the expression
                //parsing on each coprocessor
                final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);

                //each coprocessor has its own settings and field requirements
                final List<StatStoreTableCoprocessor> tableCoprocessors = coprocessorMap.entrySet().stream()
                        .map(entry ->
                                new StatStoreTableCoprocessor(
                                        entry.getKey(),
                                        (TableCoprocessorSettings) entry.getValue(),
                                        fieldIndexMap,
                                        taskContext,
                                        search.getParamMap()))
                        .collect(ImmutableList.toImmutableList());

                // convert the search into something stats understands
                final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(
                        search,
                        entity);

                final AtomicLong counter = new AtomicLong(0);
                taskContext.info(searchName + " - executing database query");

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
                                                // thread may be interrupted if outer onComplete is called before this one
                                                LAMBDA_LOGGER.debug(() ->
                                                        String.format("onComplete of inner flowable called, counter: %s",
                                                                counter.get()));
                                                // completed our timed window so create and pass on a payload for the
                                                // data we have gathered so far
                                                processPayloads(resultCollector, tableCoprocessors);
                                                taskContext.info(searchName +
                                                        " - running database query (" + counter.get() + " rows fetched)");
                                            });
                                },
                                throwable -> {
                                    throw new RuntimeException(String.format("Error in flow, %s",
                                            throwable.getMessage()), throwable);
                                },
                                () -> {
                                    LAMBDA_LOGGER.debug(() ->
                                            String.format("onComplete of outer flowable called, counter: %s",
                                                    counter.get()));
                                    // When this onComplete is called rxJava will call cancel on any window threads
                                    // which will issue an interrupt(). Calling processPayloads here, which is synchronised
                                    // ensures all results are processed before any threads are interrupted.
                                    processPayloads(resultCollector, tableCoprocessors);
                                });

                LOGGER.debug("Out of flowable");

                // flows all complete, so process any remaining data
                processPayloads(resultCollector, tableCoprocessors);
                taskContext.info(searchName +
                        " - completed database query (" + counter.get() + " rows fetched)");
            }

            // Let the result handler know search has finished.
            resultCollector.getResultHandler().setComplete(true);

            taskContext.info(searchName + " - complete");

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

        if (!Thread.currentThread().isInterrupted()) {
            LOGGER.debug("processPayloads called for {} coprocessors", tableCoprocessors.size());

            //build a payload map from whatever the coprocessors have in them, if anything
            final Map<Integer, Payload> payloadMap = tableCoprocessors.stream()
                    .map(tableCoprocessor ->
                            Maps.immutableEntry(tableCoprocessor.getId(), tableCoprocessor.createPayload()))
                    .filter(entry ->
                            entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // log the queue sizes in the payload map
            if (LOGGER.isDebugEnabled()) {
                final String contents = payloadMap.entrySet().stream()
                        .map(entry -> {
                            String id = Integer.toString(entry.getKey());
                            String size;
                            // entry checked for null in stream above
                            if (entry.getValue() instanceof TablePayload) {
                                TablePayload tablePayload = (TablePayload) entry.getValue();
                                if (tablePayload.getQueue() != null) {
                                    size = Integer.toString(tablePayload.getQueue().size());
                                } else {
                                    size = "null";
                                }
                            } else {
                                size = "?";
                            }
                            return id + ": " + size;
                        })
                        .collect(Collectors.joining(", "));
                LOGGER.debug("payloadMap: [{}]", contents);
            }

            // give the processed results to the collector, it will handle nulls
            resultCollector.handle(payloadMap);
        } else {
            LOGGER.debug("Thread is interrupted, not processing payload");
        }
    }

    private Consumer<Val[]> buildDataArrayConsumer(
            final Search search,
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
                fieldIndexMap, search.getParamMap());

        // Create a queue of string arrays.
        final PairQueue<String, Item> queue = new BlockingPairQueue<>(taskContext);
        final ItemMapper mapper = new ItemMapper(
                queue,
                compiledFields,
                compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        //create a consumer of the data array that will ultimately be returned from the database query
        return (Val[] data) -> {
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
