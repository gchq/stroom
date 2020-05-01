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

import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.processor.api.DataProcessorTaskExecutor;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.TaskType;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class DataProcessorTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataProcessorTaskHandler.class);

    private final Map<TaskType, Provider<DataProcessorTaskExecutor>> executorProviders;
    private final ProcessorCache processorCache;
    private final ProcessorFilterCache processorFilterCache;
    private final ProcessorTaskDao processorTaskDao;
    private final Store streamStore;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    DataProcessorTaskHandler(final Map<TaskType, Provider<DataProcessorTaskExecutor>> executorProviders,
                             final ProcessorCache processorCache,
                             final ProcessorFilterCache processorFilterCache,
                             final ProcessorTaskDao processorTaskDao,
                             final Store streamStore,
                             final NodeInfo nodeInfo,
                             final SecurityContext securityContext,
                             final TaskContextFactory taskContextFactory) {
        this.executorProviders = executorProviders;
        this.processorCache = processorCache;
        this.processorFilterCache = processorFilterCache;
        this.processorTaskDao = processorTaskDao;
        this.streamStore = streamStore;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    public ProcessorResult exec(final ProcessorTask task) {
        // Perform processing as the processing user.
        return securityContext.asProcessingUserResult(() -> {
            // Execute with a task context.
            return taskContextFactory.contextResult("Data Processor", taskContext -> exec(taskContext, task)).get();
        });
    }

    private ProcessorResult exec(final TaskContext taskContext, final ProcessorTask task) {
        ProcessorTask processorTask = task;

        boolean complete = false;
        ProcessorResult processorResult = new ProcessorResultImpl(0, 0, Collections.emptyMap());
        final long startTime = System.currentTimeMillis();
        LOGGER.trace(LambdaLogUtil.message("Executing processor task: {}", processorTask.getId()));

        // Open the stream source.
        try (Source source = streamStore.openSource(processorTask.getMetaId())) {
            if (source != null) {
                final Meta meta = source.getMeta();

                Processor destStreamProcessor = null;
                ProcessorFilter destProcessorFilter = null;
                if (processorTask.getProcessorFilter() != null) {
                    destProcessorFilter = processorFilterCache.get(processorTask.getProcessorFilter().getId()).orElse(null);
                    if (destProcessorFilter != null) {
                        destStreamProcessor = processorCache
                                .get(destProcessorFilter.getProcessor().getId()).orElse(null);
                    }
                }
                if (destProcessorFilter == null || destStreamProcessor == null) {
                    throw new RuntimeException("No dest processor has been loaded.");
                }

                log(taskContext, meta, destStreamProcessor);

                // Don't process any streams that we have already created
                if (meta.getProcessorUuid() != null && meta.getProcessorUuid().equals(destStreamProcessor.getUuid())) {
                    complete = true;
                    LOGGER.warn(LambdaLogUtil.message("Skipping data that we seem to have created (avoid processing forever) {} {}", meta,
                            destStreamProcessor));

                } else {
                    // Change the task status.... and save
                    processorTask = processorTaskDao.changeTaskStatus(processorTask, nodeInfo.getThisNodeName(),
                            TaskStatus.PROCESSING, startTime, null);
                    // Avoid having to do another fetch
                    processorTask.setProcessorFilter(destProcessorFilter);

                    final Provider<DataProcessorTaskExecutor> executorProvider = executorProviders.get(new TaskType(destStreamProcessor.getTaskType()));
                    final DataProcessorTaskExecutor dataProcessorTaskExecutor = executorProvider.get();

                    try {
                        processorResult = dataProcessorTaskExecutor
                                .exec(taskContext, destStreamProcessor, destProcessorFilter, processorTask, source);
                        // Only record completion for this task if it was not
                        // terminated.
                        if (!Thread.currentThread().isInterrupted()) {
                            complete = true;
                        }

                    } catch (final RuntimeException e) {
                        LOGGER.error(LambdaLogUtil.message("Task failed {} {}", new Object[]{destStreamProcessor, meta}, e));
                    }
                }
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            if (complete) {
                processorTaskDao.changeTaskStatus(processorTask, nodeInfo.getThisNodeName(), TaskStatus.COMPLETE,
                        startTime, System.currentTimeMillis());
            } else {
                processorTaskDao.changeTaskStatus(processorTask, nodeInfo.getThisNodeName(), TaskStatus.FAILED, startTime,
                        System.currentTimeMillis());
            }
        }

        return processorResult;
    }

    private void log(final TaskContext taskContext, final Meta meta, final Processor destStreamProcessor) {
        if (destStreamProcessor.getPipelineUuid() != null) {
            taskContext.info(() -> "Stream " +
                    meta.getId() +
                    " " +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    " " +
                    destStreamProcessor.getTaskType() +
                    " " +
                    destStreamProcessor.getPipelineUuid());
        } else {
            taskContext.info(() -> "Stream " +
                    meta.getId() +
                    " " +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    " " +
                    destStreamProcessor.getTaskType());
        }
    }
}
