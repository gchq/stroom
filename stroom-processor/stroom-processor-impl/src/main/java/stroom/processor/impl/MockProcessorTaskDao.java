package stroom.processor.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.ExpressionUtil;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Clearable;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Singleton
public class MockProcessorTaskDao implements ProcessorTaskDao, Clearable {
    private final MockIntCrud<ProcessorTask> dao = new MockIntCrud<>();
//
//    @Override
//    public ProcessorTask create(final ProcessorTask processorTask) {
//        return dao.create(processorTask);
//    }
//
//    @Override
//    public Optional<ProcessorTask> fetch(final int id) {
//        return dao.fetch(id);
//    }
//
//    @Override
//    public ProcessorTask update(final ProcessorTask processorTask) {
//        return dao.update(processorTask);
//    }
//
//    @Override
//    public boolean delete(final int id) {
//        return dao.delete(id);
//    }
//
//    @Override
//    public BaseResultList<ProcessorTask> find(final FindProcessorTaskCriteria criteria) {
//        final List<ProcessorTask> list = dao
//                .getMap()
//                .values()
//                .stream()
////                .filter(pf -> criteria.getPipelineUuidCriteria().getString().equals(pf.getPipelineUuid()))
//                .collect(Collectors.toList());
//
//        return BaseResultList.createCriterialBasedList(list, criteria);
//    }


    @Override
    public void releaseOwnedTasks() {
        final long now = System.currentTimeMillis();
        dao.getMap().values().forEach(task -> {
            if (TaskStatus.UNPROCESSED.equals(task.getStatus()) ||
                    TaskStatus.ASSIGNED.equals(task.getStatus()) ||
                    TaskStatus.PROCESSING.equals(task.getStatus())) {
                task.setStatus(TaskStatus.UNPROCESSED);
                task.setStatusTimeMs(now);
                task.setNodeName(null);
                dao.update(task);
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
                               final Consumer<CreatedTasks> consumer) {
        final long now = System.currentTimeMillis();

        metaMap.forEach((meta, eventRanges) -> {
            final ProcessorTask task = new ProcessorTask();
            task.setVersion(1);
            task.setCreateTimeMs(now);
            task.setStatus(TaskStatus.UNPROCESSED);
            task.setStartTimeMs(now);

            if (Status.UNLOCKED.equals(meta.getStatus())) {
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
    public ProcessorTask changeTaskStatus(final ProcessorTask processorTask, final String nodeName, final TaskStatus status, final Long startTime, final Long endTime) {
        processorTask.setNodeName(nodeName);
        processorTask.setStatus(status);
        processorTask.setStatusTimeMs(System.currentTimeMillis());
        processorTask.setStartTimeMs(startTime);
        processorTask.setEndTimeMs(endTime);
        return processorTask;
    }

    @Override
    public BaseResultList<ProcessorTask> find(final ExpressionCriteria criteria) {
        final List<ProcessorTask> list = dao
                .getMap()
                .values()
                .stream()
                .filter(task -> {
                    final List<String> pipelineUuids = ExpressionUtil.values(criteria.getExpression(), ProcessorTaskDataSource.PIPELINE_UUID);
                    if (pipelineUuids != null) {
                        if (!pipelineUuids.contains(task.getProcessorFilter().getProcessor().getPipelineUuid())) {
                            return false;
                        }
                    }
                    final List<String> taskStatus = ExpressionUtil.values(criteria.getExpression(), ProcessorTaskDataSource.STATUS);
                    if (taskStatus != null) {
                        if (!taskStatus.contains(task.getStatus().getDisplayValue())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        return BaseResultList.createCriterialBasedList(list, criteria);
    }

    @Override
    public BaseResultList<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria, final AbstractField[] fields, final Consumer<Val[]> consumer) {

    }

    @Override
    public void clear() {
        dao.clear();
    }
}
