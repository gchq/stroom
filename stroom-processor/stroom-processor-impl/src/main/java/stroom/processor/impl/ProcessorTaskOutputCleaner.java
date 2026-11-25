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

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Logically deletes the output streams of processor tasks that must not be kept. All of this must be done as the
/// processing user, as the user that a filter runs as may not have permission to delete them.
class ProcessorTaskOutputCleaner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskOutputCleaner.class);

    private static final int BATCH_SIZE = 1_000;

    // The statuses of tasks that won't write any more output. A task waiting to be tried again isn't one of them, as
    // it clears its own output when it is.
    // The statuses of tasks that finished well, of which the newest one's output is kept.
    private static final Set<TaskStatus> KEPT = EnumSet.of(TaskStatus.COMPLETE, TaskStatus.COMMITTING);
    private static final Set<TaskStatus> FINISHED = EnumSet.of(
            TaskStatus.COMPLETE,
            TaskStatus.COMMITTING,
            TaskStatus.ABORTED,
            TaskStatus.DELETED);

    private final MetaService metaService;
    private final ProcessorTaskDao processorTaskDao;

    @Inject
    ProcessorTaskOutputCleaner(final MetaService metaService,
                               final ProcessorTaskDao processorTaskDao) {
        this.metaService = metaService;
        this.processorTaskDao = processorTaskDao;
    }

    /// Deletes everything that earlier runs of a task wrote, which must be done before the run that calls this has
    /// opened anything.
    ///
    /// @param task   The task as the run knows it.
    /// @param isHeld Finds out whether the run still has its task. This is asked after the streams to delete have
    ///               been found, and before they are deleted. A run that stalled, and was replaced meanwhile, can
    ///               then never delete anything that the run that replaced it has written.
    /// @return False if the run no longer has its task, so must not go on.
    /// @throws RuntimeException If any of it could not be deleted.
    boolean clearEarlierRuns(final ProcessorTask task, final BooleanSupplier isHeld) {
        while (true) {
            final List<Long> metaIds = findOutput(task);
            if (!isHeld.getAsBoolean()) {
                return false;
            }
            if (metaIds.isEmpty()) {
                return true;
            }
            // This throws an exception if any are left, so can't go round forever.
            delete(metaIds);
            LOGGER.info(() -> LogUtil.message("Deleted {} streams written by earlier runs of processor task {}",
                    metaIds.size(), task.getId()));
        }
    }

    private List<Long> findOutput(final ProcessorTask task) {
        final FindMetaCriteria criteria = new FindMetaCriteria(createOutputExpression(task.getMetaId())
                .addIdTerm(MetaFields.META_PROCESSOR_TASK_ID, Condition.EQUALS, task.getId())
                .build());
        criteria.setPageRequest(new PageRequest(0, BATCH_SIZE));
        return metaService.find(criteria)
                .getValues()
                .stream()
                .map(Meta::getId)
                .toList();
    }

    /// Deletes output of the same processor's pipeline on the same stream that is out of date, now that a task has
    /// finished. This must only be done once the status of the task has been set.
    ///
    /// Of the tasks that have finished well, the output of the one with the highest id is kept, as it is the one
    /// that was made last, e.g. to reprocess the stream. Output of every other task that has finished, or no longer
    /// exists, is deleted, even if that is the output of the task that has just finished. Every node then deletes the
    /// same output, however close together the tasks finish, so the output of both is never deleted. The output of
    /// a task that hasn't finished is kept.
    ///
    /// @param task      The task that has finished.
    /// @param meta      The stream that it processed.
    /// @param processor The processor of the task.
    void clearSuperseded(final ProcessorTask task, final Meta meta, final Processor processor) {
        final FindMetaCriteria criteria = new FindMetaCriteria(createOutputExpression(meta.getId())
                .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, processor.getPipeline())
                .build());
        final List<Meta> outputs = metaService.find(criteria).getValues();
        if (outputs.stream().allMatch(output -> isOwn(output, task))) {
            return;
        }

        // Tasks of every processor, so that a task of another processor that uses the same pipeline isn't taken for
        // one that has finished.
        final Map<Long, ProcessorTask> tasks = findTasks(meta);
        final long newestId = tasks
                .values()
                .stream()
                .filter(other -> KEPT.contains(other.getStatus()))
                .mapToLong(ProcessorTask::getId)
                .reduce(task.getId(), Math::max);
        final List<Long> superseded = outputs
                .stream()
                .filter(output -> {
                    if (output.getProcessorTaskId() == null) {
                        return true;
                    }
                    final long taskId = output.getProcessorTaskId();
                    final ProcessorTask other = taskId == task.getId()
                            ? task
                            : tasks.get(taskId);
                    // If there is no longer a task then it must have finished.
                    if (other == null) {
                        return true;
                    }
                    if (KEPT.contains(other.getStatus()) || taskId == task.getId()) {
                        return taskId != newestId;
                    }
                    return FINISHED.contains(other.getStatus());
                })
                .map(Meta::getId)
                .toList();
        delete(superseded);
        if (!superseded.isEmpty()) {
            LOGGER.info(() -> LogUtil.message("Deleted {} out of date streams of stream {} when processor task {} "
                                              + "finished", superseded.size(), meta.getId(), task.getId()));
        }
    }

    private boolean isOwn(final Meta output, final ProcessorTask task) {
        return output.getProcessorTaskId() != null && output.getProcessorTaskId() == task.getId();
    }

    private Map<Long, ProcessorTask> findTasks(final Meta meta) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(ProcessorTaskFields.META_ID, Condition.EQUALS, meta.getId())
                .build();
        return processorTaskDao.find(new ExpressionCriteria(expression))
                .stream()
                .collect(Collectors.toMap(ProcessorTask::getId, Function.identity()));
    }

    private ExpressionOperator.Builder createOutputExpression(final long parentMetaId) {
        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, parentMetaId)
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.toString())
                        .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.toString())
                        .build());
    }

    /// Deletes streams, whether they are locked or not. It doesn't matter if any have been deleted already.
    ///
    /// @param metaIds The ids of the streams.
    /// @throws RuntimeException If any could not be deleted. This is after trying to delete them all.
    void delete(final Collection<Long> metaIds) {
        RuntimeException exception = null;
        for (final long metaId : metaIds) {
            try {
                delete(metaId);
            } catch (final RuntimeException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    private void delete(final long metaId) {
        if (metaService.delete(metaId, false) == 0) {
            // Nothing is done, and nothing is said, if it has gone already or if there is no permission to delete it.
            final Meta meta = metaService.getMeta(metaId, true);
            if (meta != null && !Status.DELETED.equals(meta.getStatus())) {
                throw new RuntimeException("Unable to delete stream " + metaId);
            }
        }
    }
}
