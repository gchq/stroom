/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl;

import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.ProcessorTaskExecutor;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.ProcessingException;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class DataProcessorTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataProcessorTaskHandler.class);

    private final Map<ProcessorType, Provider<ProcessorTaskExecutor>> executorProviders;
    private final ProcessorFilterCache processorFilterCache;
    private final ProcessorTaskDao processorTaskDao;
    private final Store streamStore;
    private final MetaService metaService;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    DataProcessorTaskHandler(final Map<ProcessorType, Provider<ProcessorTaskExecutor>> executorProviders,
                             final ProcessorFilterCache processorFilterCache,
                             final ProcessorTaskDao processorTaskDao,
                             final Store streamStore,
                             final MetaService metaService,
                             final NodeInfo nodeInfo,
                             final SecurityContext securityContext,
                             final TaskContextFactory taskContextFactory) {
        this.executorProviders = executorProviders;
        this.processorFilterCache = processorFilterCache;
        this.processorTaskDao = processorTaskDao;
        this.streamStore = streamStore;
        this.metaService = metaService;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    public ProcessorResult exec(final ProcessorTask task) {
        // Perform processing as the filter owner.
        final UserRef runAsUser = getFilterRunAs(task.getProcessorFilter());
        return securityContext.asUserResult(runAsUser, () -> securityContext.useAsReadResult(() -> {
            // Execute with a task context.
            return taskContextFactory.contextResult(
                    "Data Processor",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> exec(taskContext, task)).get();
        }));
    }

    private UserRef getFilterRunAs(final ProcessorFilter filter) {
        if (filter.getRunAsUser() == null) {
            throw new RuntimeException(
                    LogUtil.message("No run as user specified for filter uuid: {}", filter.getUuid()));
        }
        return filter.getRunAsUser();
    }

    private ProcessorResult exec(final TaskContext taskContext, final ProcessorTask task) {
        ProcessorTask processorTask = task;

        boolean complete = false;
        ProcessorResult processorResult = new ProcessorResultImpl(0, 0, Collections.emptyMap());
        final long startTime = System.currentTimeMillis();

        // Have to do the if as processorTask is not final so can't use a lambda
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Executing processor task: {}", processorTask.getId());
        }

        // Open the stream source.
        try (final Source source = streamStore.openSource(processorTask.getMetaId())) {
            if (source == null) {
                throw new ProcessingException("Source not found for " + processorTask.getMetaId());
            }

            final Meta meta = source.getMeta();

            Processor processor = null;
            ProcessorFilter processorFilter = null;
            if (processorTask.getProcessorFilter() != null) {
                processorFilter = processorFilterCache.get(processorTask.getProcessorFilter().getId()).orElse(
                        null);
                if (processorFilter != null) {
                    processor = processorFilter.getProcessor();
                }
            }
            if (processorFilter == null || processor == null) {
                throw new ProcessingException("No dest processor has been loaded.");
            }

            log(taskContext, meta, processor);

            // Don't process any streams that we have already created
            if (meta.getProcessorUuid() != null && meta.getProcessorUuid().equals(processor.getUuid())) {
                complete = true;
                // Have to do the if as processorTask is not final so can't use a lambda
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Skipping data that we seem to have created (avoid processing forever) {} {}",
                            meta, processor);
                }

            } else {
                // Change the task status.... and save
                processorTask = processorTaskDao.changeTaskStatus(processorTask, nodeInfo.getThisNodeName(),
                        TaskStatus.PROCESSING, startTime, null);
                if (processorTask != null) {
                    // Avoid having to do another fetch
                    processorTask.setProcessorFilter(processorFilter);

                    final Provider<ProcessorTaskExecutor> executorProvider = executorProviders.get(
                            processor.getProcessorType());
                    final ProcessorTaskExecutor processorTaskExecutor = executorProvider.get();

                    try {
                        processorResult = processorTaskExecutor
                                .exec(taskContext, processor, processorFilter, processorTask, source);
                        // Only record completion for this task if it was not
                        // terminated.
                        if (!taskContext.isTerminated()) {
                            complete = true;
                        }

                    } catch (final RuntimeException e) {
                        throw new ProcessingException("Task failed " + processor + " " + meta, e);
                    }
                } else {
                    LOGGER.debug("Null processorTask. " +
                            "Task may have been logically/physically deleted so nothing to do");
                }
            }
        } catch (final IOException | RuntimeException e) {
            // Check to see if the meta has been deleted.
            final Meta meta = securityContext.asProcessingUserResult(() ->
                    metaService.getMeta(task.getMetaId(), true));

            // If meta has been deleted then allow normal completion.
            if (meta == null || Status.DELETED.equals(meta.getStatus())) {
                LOGGER.debug(e::getMessage, e);
                complete = true;

            } else {
                LOGGER.error(e::getMessage, e);
            }
        } finally {
            // Null processorTask implies the task was (logically)? deleted before we completed so no point in
            // changing status
            if (processorTask != null) {
                if (complete) {
                    processorTaskDao.changeTaskStatus(processorTask, nodeInfo.getThisNodeName(), TaskStatus.COMPLETE,
                            startTime, System.currentTimeMillis());
                } else {
                    processorTaskDao.changeTaskStatus(processorTask,
                            nodeInfo.getThisNodeName(),
                            TaskStatus.FAILED,
                            startTime,
                            System.currentTimeMillis());
                }
            }
        }

        return processorResult;
    }

    private void log(final TaskContext taskContext, final Meta meta, final Processor destStreamProcessor) {
        if (destStreamProcessor.getPipelineUuid() != null) {
            taskContext.info(() -> "meta_id=" +
                    meta.getId() +
                    ", created=" +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    ", processor type=" +
                    destStreamProcessor.getProcessorType() +
                    ", pipeline uuid=" +
                    destStreamProcessor.getPipelineUuid());
        } else {
            taskContext.info(() -> "meta_id=" +
                    meta.getId() +
                    ", created=" +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    ", processor type=" +
                    destStreamProcessor.getProcessorType());
        }
    }
}
