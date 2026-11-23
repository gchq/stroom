/*
 * Copyright 2026 Crown Copyright
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


package stroom.core.dataprocess;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.processor.api.ProcessorTaskRecovery;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Singleton
class ProcessorTaskRecoveryImpl implements ProcessorTaskRecovery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskRecoveryImpl.class);

    // What an error stream line names as the source of the message, where a pipeline run names an element.
    private static final String SOURCE = "IndexRecovery";

    private final ProcessorTaskService processorTaskService;
    private final MetaService metaService;
    private final Store streamStore;
    private final VolumeGroupNameProvider volumeGroupNameProvider;
    private final SecurityContext securityContext;

    @Inject
    ProcessorTaskRecoveryImpl(final ProcessorTaskService processorTaskService,
                              final MetaService metaService,
                              final Store streamStore,
                              final VolumeGroupNameProvider volumeGroupNameProvider,
                              final SecurityContext securityContext) {
        this.processorTaskService = processorTaskService;
        this.metaService = metaService;
        this.streamStore = streamStore;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
        this.securityContext = securityContext;
    }

    @Override
    public Optional<TaskStatus> getStatus(final long taskId) {
        return securityContext.asProcessingUserResult(() ->
                processorTaskService.fetch(taskId).map(ProcessorTask::getStatus));
    }

    @Override
    public boolean retry(final long taskId, final String message) {
        return securityContext.asProcessingUserResult(() -> {
            final Optional<ProcessorTask> optional = processorTaskService.fetch(taskId);
            if (optional.isEmpty()) {
                return true;
            }
            final ProcessorTask task = optional.get();
            return switch (task.getStatus()) {
                case PROCESSING -> false;
                case DELETED -> true;
                case COMPLETE, FAILED -> {
                    if (processorTaskService.returnToCreated(taskId)) {
                        LOGGER.info(() -> LogUtil.message(
                                "Processor task {} for stream {} will be run again: {}",
                                taskId, task.getMetaId(), message));
                        deleteOutputOfEarlierRun(task);
                        writeErrorStream(task, message);
                        yield true;
                    }
                    // Its status changed as this looked.
                    yield false;
                }
                // It is waiting to be run again already.
                case CREATED, QUEUED, ASSIGNED -> {
                    writeErrorStream(task, message);
                    yield true;
                }
            };
        });
    }

    @Override
    public boolean fail(final long taskId, final String message) {
        return securityContext.asProcessingUserResult(() -> {
            final Optional<ProcessorTask> optional = processorTaskService.fetch(taskId);
            if (optional.isEmpty()) {
                return true;
            }
            final ProcessorTask task = optional.get();
            return switch (task.getStatus()) {
                case PROCESSING -> false;
                case DELETED -> true;
                case FAILED -> {
                    writeErrorStream(task, message);
                    yield true;
                }
                case CREATED, QUEUED, ASSIGNED, COMPLETE -> {
                    if (processorTaskService.markFailed(taskId)) {
                        LOGGER.error(() -> LogUtil.message(
                                "Processor task {} for stream {} has been set to failed: {}",
                                taskId, task.getMetaId(), message));
                        writeErrorStream(task, message);
                        yield true;
                    }
                    yield false;
                }
            };
        });
    }

    // Deletes the streams the task wrote when it last ran, its Error stream included. Running a pipeline
    // clears away the earlier output of other tasks but keeps anything carrying the running task's own id,
    // and this task is about to run again under the same id. Left alone its first run's output would stay
    // beside the second's for good.
    private void deleteOutputOfEarlierRun(final ProcessorTask task) {
        try {
            if (task.getMetaId() == null) {
                return;
            }
            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, task.getMetaId())
                    .addIdTerm(MetaFields.META_PROCESSOR_TASK_ID, Condition.EQUALS, task.getId())
                    .addOperator(ExpressionOperator
                            .builder()
                            .op(Op.OR)
                            .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.toString())
                            .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.toString())
                            .build())
                    .build();
            for (final Meta output : metaService.find(new FindMetaCriteria(expression)).getValues()) {
                metaService.updateStatus(output, output.getStatus(), Status.DELETED);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Unable to delete the earlier output of task {}: {}", task.getId(), e.getMessage()), e);
        }
    }

    // Writes an Error stream against the task's source stream, as running the task's pipeline does when it
    // fails. It carries no task id. An error stream with the id of a task is kept for as long as that task
    // is the latest to process the stream, and this task is about to run again under the same id, so the
    // message would stay beside a successful run for good. Without an id it is removed at the end of the
    // next run of the pipeline on the stream.
    private void writeErrorStream(final ProcessorTask task, final String message) {
        try {
            final Meta meta = NullSafe.get(task.getMetaId(), metaId -> metaService.getMeta(metaId, true));
            if (meta == null) {
                LOGGER.debug(() -> "No stream to write an error stream against for task " + task.getId());
                return;
            }
            final ProcessorFilter filter = task.getProcessorFilter();
            final Processor processor = NullSafe.get(filter, ProcessorFilter::getProcessor);
            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(meta.getFeedName())
                    .typeName(StreamTypeNames.ERROR)
                    .parent(meta)
                    .processorUuid(NullSafe.get(processor, Processor::getUuid))
                    .pipelineUuid(NullSafe.get(filter, ProcessorFilter::getPipelineUuid))
                    .processorFilterId(NullSafe.get(filter, ProcessorFilter::getId))
                    .build();
            final String volumeGroupName = volumeGroupNameProvider
                    .getVolumeGroupName(meta.getFeedName(), StreamTypeNames.ERROR, null);
            final String line = "[" + SOURCE + "] " + Severity.ERROR.getDisplayValue() + ": "
                                + message.replace('\n', ' ').replace("\r", "") + "\n";
            try (final Target target = streamStore.openTarget(metaProperties, volumeGroupName)) {
                try (final OutputStream outputStream = target.next().get()) {
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (final RuntimeException e) {
            // The task has been dealt with all the same. This is the message that would have said so.
            LOGGER.error(() -> LogUtil.message(
                    "Unable to write an error stream for task {} saying: {}", task.getId(), message), e);
        }
    }
}
