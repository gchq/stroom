package stroom.processor.impl.db;

import org.jooq.Record;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;
import static stroom.processor.impl.db.jooq.tables.ProcessorFeed.PROCESSOR_FEED;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class RecordToProcessorTaskMapper implements Function<Record, ProcessorTask> {
    @Override
    public ProcessorTask apply(final Record record) {
        final ProcessorTask processorTask = new ProcessorTask();
        processorTask.setId(record.get(PROCESSOR_TASK.ID));
        processorTask.setVersion(record.get(PROCESSOR_TASK.VERSION));
        processorTask.setMetaId(record.get(PROCESSOR_TASK.META_ID));
        processorTask.setData(record.get(PROCESSOR_TASK.DATA));
        if (record.field(PROCESSOR_NODE.NAME) != null) {
            processorTask.setNodeName(record.get(PROCESSOR_NODE.NAME));
        }
        if (record.field(PROCESSOR_FEED.NAME) != null) {
            processorTask.setFeedName(record.get(PROCESSOR_FEED.NAME));
        }
        processorTask.setStatus(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(PROCESSOR_TASK.STATUS)));
        processorTask.setStartTimeMs(record.get(PROCESSOR_TASK.START_TIME_MS));
        processorTask.setCreateTimeMs(record.get(PROCESSOR_TASK.CREATE_TIME_MS));
        processorTask.setStatusTimeMs(record.get(PROCESSOR_TASK.STATUS_TIME_MS));
        processorTask.setEndTimeMs(record.get(PROCESSOR_TASK.END_TIME_MS));
        return processorTask;
    }
}
