package stroom.processor.impl.db;

import org.jooq.Record;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.TaskStatus;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTask.PROCESSOR_FILTER_TASK;
import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;

class RecordToProcessorFilterTaskMapper implements Function<Record, ProcessorFilterTask> {
    @Override
    public ProcessorFilterTask apply(final Record record) {
        final ProcessorFilterTask processorFilterTask = new ProcessorFilterTask();
        processorFilterTask.setId(record.get(PROCESSOR_FILTER_TASK.ID));
        processorFilterTask.setVersion(record.get(PROCESSOR_FILTER_TASK.VERSION).intValue()); // TODO : @66 Remove when version is an integer
        processorFilterTask.setMetaId(record.get(PROCESSOR_FILTER_TASK.META_ID));
        processorFilterTask.setData(record.get(PROCESSOR_FILTER_TASK.DATA));
        processorFilterTask.setNodeName(record.get(PROCESSOR_NODE.NAME));
        processorFilterTask.setStatus(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(PROCESSOR_FILTER_TASK.STATUS)));
        processorFilterTask.setStartTimeMs(record.get(PROCESSOR_FILTER_TASK.STATUS_TIME_MS));
        processorFilterTask.setCreateMs(record.get(PROCESSOR_FILTER_TASK.CREATE_TIME_MS));
        processorFilterTask.setStatusMs(record.get(PROCESSOR_FILTER_TASK.STATUS_TIME_MS));
        processorFilterTask.setEndTimeMs(record.get(PROCESSOR_FILTER_TASK.END_TIME_MS));
        return processorFilterTask;
    }
}
