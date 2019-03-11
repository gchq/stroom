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

package stroom.processor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.processor.api.DataProcessorTaskExecutor;
import stroom.processor.api.TaskType;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.util.Map;

public class DataProcessorTaskHandler extends AbstractTaskHandler<DataProcessorTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataProcessorTaskHandler.class);
    //    private static final Set<String> FETCH_SET = new HashSet<>(
//            Arrays.asList(Processor.ENTITY_TYPE, ProcessorFilter.ENTITY_TYPE));
    private final Map<TaskType, Provider<DataProcessorTaskExecutor>> executorProviders;
    private final ProcessorCache processorCache;
    private final ProcessorFilterCache processorFilterCache;
    private final ProcessorFilterTaskCreator processorFilterTaskCreator;
    private final Store streamStore;
    private final NodeInfo nodeInfo;
    private final TaskContext taskContext;
    private final Security security;

    @Inject
    DataProcessorTaskHandler(final Map<TaskType, Provider<DataProcessorTaskExecutor>> executorProviders,
                             final ProcessorCache processorCache,
                             final ProcessorFilterCache processorFilterCache,
                             final ProcessorFilterTaskCreator processorFilterTaskCreator,
                             final Store streamStore,
                             final NodeInfo nodeInfo,
                             final TaskContext taskContext,
                             final Security security) {
        this.executorProviders = executorProviders;
        this.processorCache = processorCache;
        this.processorFilterCache = processorFilterCache;
        this.processorFilterTaskCreator = processorFilterTaskCreator;
        this.streamStore = streamStore;
        this.nodeInfo = nodeInfo;
        this.taskContext = taskContext;
        this.security = security;
    }

    @Override
    public VoidResult exec(final DataProcessorTask task) {
        return security.secureResult(() -> {
            boolean complete = false;
            final long startTime = System.currentTimeMillis();
            ProcessorFilterTask streamTask = task.getProcessorFilterTask();
            LOGGER.trace("Executing stream task: {}", streamTask.getId());

            // Open the stream source.
            try (Source source = streamStore.openSource(streamTask.getMetaId())) {
                if (source != null) {
                    final Meta meta = source.getMeta();

                    Processor destStreamProcessor = null;
                    ProcessorFilter destProcessorFilter = null;
                    if (streamTask.getProcessorFilter() != null) {
                        destProcessorFilter = processorFilterCache.get(streamTask.getProcessorFilter().getId()).orElse(null);
                        if (destProcessorFilter != null) {
                            destStreamProcessor = processorCache
                                    .get(destProcessorFilter.getProcessor().getId()).orElse(null);
                        }
                    }
                    if (destProcessorFilter == null || destStreamProcessor == null) {
                        throw new RuntimeException("No dest processor has been loaded.");
                    }

                    if (destStreamProcessor.getPipelineUuid() != null) {
                        taskContext.info("Stream {} {} {} {}", meta.getId(),
                                DateUtil.createNormalDateTimeString(meta.getCreateMs()),
                                destStreamProcessor.getTaskType(), destStreamProcessor.getPipelineUuid());
                    } else {
                        taskContext.info("Stream {} {} {}", meta.getId(),
                                DateUtil.createNormalDateTimeString(meta.getCreateMs()),
                                destStreamProcessor.getTaskType());
                    }

                    // Don't process any streams that we have already created
                    if (meta.getProcessorUuid() != null && meta.getProcessorUuid().equals(destStreamProcessor.getUuid())) {
                        complete = true;
                        LOGGER.warn("Skipping data that we seem to have created (avoid processing forever) {} {}", meta,
                                destStreamProcessor);

                    } else {
                        // Change the task status.... and save
                        streamTask = processorFilterTaskCreator.changeTaskStatus(streamTask, nodeInfo.getThisNodeName(),
                                TaskStatus.PROCESSING, startTime, null);
                        // Avoid having to do another fetch
                        streamTask.setProcessorFilter(destProcessorFilter);

                        final Provider<DataProcessorTaskExecutor> executorProvider = executorProviders.get(new TaskType(destStreamProcessor.getTaskType()));
                        final DataProcessorTaskExecutor dataProcessorTaskExecutor = executorProvider.get();

                        // Used as a hook for the test code
                        task.setDataProcessorTaskExecutor(dataProcessorTaskExecutor);

                        try {
                            dataProcessorTaskExecutor.exec(destStreamProcessor, destProcessorFilter, streamTask,
                                    source);
                            // Only record completion for this task if it was not
                            // terminated.
                            if (!Thread.currentThread().isInterrupted()) {
                                complete = true;
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error("Task failed {} {}", new Object[]{destStreamProcessor, meta}, e);
                        }
                    }
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                if (complete) {
                    processorFilterTaskCreator.changeTaskStatus(streamTask, nodeInfo.getThisNodeName(), TaskStatus.COMPLETE,
                            startTime, System.currentTimeMillis());
                } else {
                    processorFilterTaskCreator.changeTaskStatus(streamTask, nodeInfo.getThisNodeName(), TaskStatus.FAILED, startTime,
                            System.currentTimeMillis());
                }
            }

            return new VoidResult();
        });
    }
}
