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

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionUtil;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class MockProcessorTaskDao implements ProcessorTaskDao, Clearable {

    private final MockIntCrud<ProcessorTask> dao = new MockIntCrud<>();

    @Override
    public long releaseOwnedTasks(final String nodeName) {
        return releaseTasks(Set.of(nodeName), null, null);
    }

    @Override
    public long retainOwnedTasks(final Set<String> retainForNodes,
                                 final Instant statusOlderThan) {
        return releaseTasks(null, retainForNodes, statusOlderThan);
    }

    private long releaseTasks(final Set<String> releaseForNodes,
                              final Set<String> retainForNodes,
                              final Instant statusOlderThan) {
        final long now = System.currentTimeMillis();
        dao.getMap().values().forEach(task -> {
            if (TaskStatus.CREATED.equals(task.getStatus()) ||
                    TaskStatus.PROCESSING.equals(task.getStatus())) {

                boolean release = false;
                if (releaseForNodes != null) {
                    for (final String node : releaseForNodes) {
                        if (node.equals(task.getNodeName())) {
                            release = true;
                        }
                    }

                } else if (retainForNodes != null) {
                    release = true;
                    for (final String node : retainForNodes) {
                        if (node.equals(task.getNodeName())) {
                            release = false;
                        }
                    }
                }

                if (release && statusOlderThan != null) {
                    if (statusOlderThan.toEpochMilli() < task.getStatusTimeMs()) {
                        release = false;
                    }
                }

                if (release) {
                    task.setStatus(TaskStatus.CREATED);
                    task.setStatusTimeMs(now);
                    task.setNodeName(null);
                    dao.update(task);
                }
            }
        });
        return 0L;
    }

    @Override
    public int createNewTasks(final ProcessorFilter filter,
                              final ProcessorFilterTracker tracker,
                              final FilterProgressMonitor filterProgressMonitor,
                              final long metaQueryTime,
                              final Map<Meta, InclusiveRanges> metaMap,
                              final Long maxMetaId,
                              final boolean reachedLimit) {
        final long now = System.currentTimeMillis();

        metaMap.forEach((meta, eventRanges) -> {
            final ProcessorTask task = new ProcessorTask();
            task.setVersion(1);
            task.setCreateTimeMs(now);
            task.setStatus(TaskStatus.CREATED);
            task.setStartTimeMs(now);
            task.setMetaId(meta.getId());

            String eventRangeData = null;
            if (eventRanges != null) {
                eventRangeData = eventRanges.rangesToString();
//                eventCount += eventRanges.count();
            }

            if (eventRangeData != null && !eventRangeData.isEmpty()) {
                task.setData(eventRangeData);
            }

            task.setProcessorFilter(filter);

            dao.create(task);
        });

        return metaMap.size();
    }

    @Override
    public int countTasksForFilter(final int filterId, final TaskStatus status) {
        return 0;
    }

    @Override
    public List<ProcessorTask> queueTasks(final Set<Long> idSet,
                                          final String thisNodeName) {
        return Collections.emptyList();
    }

    @Override
    public int releaseTasks(final Set<Long> idSet, final TaskStatus currentStatus) {
        return 0;
    }

    @Override
    public ResultPage<ProcessorTask> changeTaskStatus(final ExpressionCriteria criteria,
                                                      final String nodeName,
                                                      final TaskStatus status,
                                                      final Long startTime,
                                                      final Long endTime) {
        final ResultPage<ProcessorTask> tasks = find(criteria);
        tasks.forEach(task -> changeTaskStatus(task, nodeName, status, startTime, endTime));
        return tasks;
    }

    @Override
    public ProcessorTask changeTaskStatus(final ProcessorTask processorTask,
                                          final String nodeName,
                                          final TaskStatus status,
                                          final Long startTime,
                                          final Long endTime) {
        processorTask.setNodeName(nodeName);
        processorTask.setStatus(status);
        processorTask.setStatusTimeMs(System.currentTimeMillis());
        processorTask.setStartTimeMs(startTime);
        processorTask.setEndTimeMs(endTime);
        return processorTask;
    }

    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        final List<ProcessorTask> list = dao
                .getMap()
                .values()
                .stream()
                .filter(task -> {
                    final List<String> pipelineUuids = ExpressionUtil.values(criteria.getExpression(),
                            ProcessorTaskFields.PIPELINE.getFldName());
                    if (pipelineUuids != null) {
                        if (!pipelineUuids.contains(task.getProcessorFilter().getProcessor().getPipelineUuid())) {
                            return false;
                        }
                    }
                    final List<String> taskStatus = ExpressionUtil.values(criteria.getExpression(),
                            ProcessorTaskFields.STATUS.getFldName());
                    if (taskStatus != null) {
                        return taskStatus.contains(task.getStatus().getDisplayValue());
                    }
                    return true;
                })
                .toList();

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {

    }

    @Override
    public void clear() {
        dao.clear();
    }

    @Override
    public int logicalDeleteByProcessorId(final int processorId) {
        return 0;
    }

    @Override
    public int logicalDeleteByProcessorFilterId(final int processorFilterId) {
        return 0;
    }

    @Override
    public int logicalDeleteForDeletedProcessorFilters(final Instant deleteThreshold) {
        return 0;
    }

    @Override
    public int physicallyDeleteOldTasks(final Instant deleteThreshold) {
        return 0;
    }

    @Override
    public List<ExistingCreatedTask> findExistingCreatedTasks(final long minTaskId,
                                                              final int filterId,
                                                              final int limit) {
        return null;
    }
}
