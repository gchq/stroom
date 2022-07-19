package stroom.processor.impl;

import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.v2.ExpressionUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class MockProcessorTaskDao implements ProcessorTaskDao, Clearable {

    private final MockIntCrud<ProcessorTask> dao = new MockIntCrud<>();

    @Override
    public void releaseOwnedTasks(final String nodeName) {
        releaseTasks(Set.of(nodeName), null, null);
    }

    @Override
    public void retainOwnedTasks(final Set<String> retainForNodes,
                                 final Instant statusOlderThan) {
        releaseTasks(null, retainForNodes, statusOlderThan);
    }

    private void releaseTasks(final Set<String> releaseForNodes,
                              final Set<String> retainForNodes,
                              final Instant statusOlderThan) {
        final long now = System.currentTimeMillis();
        dao.getMap().values().forEach(task -> {
            if (TaskStatus.UNPROCESSED.equals(task.getStatus()) ||
                    TaskStatus.ASSIGNED.equals(task.getStatus()) ||
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
                    task.setStatus(TaskStatus.UNPROCESSED);
                    task.setStatusTimeMs(now);
                    task.setNodeName(null);
                    dao.update(task);
                }
            }
        });
    }

    @Override
    public void createNewTasks(final ProcessorFilter filter,
                               final ProcessorFilterTracker tracker,
                               final long metaQueryTime,
                               final Map<Meta, InclusiveRanges> metaMap,
                               final String thisNodeName,
                               final Long maxMetaId,
                               final boolean reachedLimit,
                               final boolean assignNewTasks,
                               final Consumer<CreatedTasks> consumer) {
        final long now = System.currentTimeMillis();

        metaMap.forEach((meta, eventRanges) -> {
            final ProcessorTask task = new ProcessorTask();
            task.setVersion(1);
            task.setCreateTimeMs(now);
            task.setStatus(TaskStatus.UNPROCESSED);
            task.setStartTimeMs(now);

            if (assignNewTasks && Status.UNLOCKED.equals(meta.getStatus())) {
                task.setNodeName(thisNodeName);
//                availableTasksCreated++;
            }

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
                            ProcessorTaskFields.PIPELINE);
                    if (pipelineUuids != null) {
                        if (!pipelineUuids.contains(task.getProcessorFilter().getProcessor().getPipelineUuid())) {
                            return false;
                        }
                    }
                    final List<String> taskStatus = ExpressionUtil.values(criteria.getExpression(),
                            ProcessorTaskFields.STATUS);
                    if (taskStatus != null) {
                        return taskStatus.contains(task.getStatus().getDisplayValue());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final ValuesConsumer consumer) {
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
