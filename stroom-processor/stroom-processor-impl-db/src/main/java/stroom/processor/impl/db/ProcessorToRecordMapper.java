package stroom.processor.impl.db;

import stroom.processor.impl.db.jooq.tables.records.ProcessorRecord;
import stroom.processor.shared.Processor;

import java.util.function.BiFunction;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;

public class ProcessorToRecordMapper implements BiFunction<Processor, ProcessorRecord, ProcessorRecord> {

    @Override
    public ProcessorRecord apply(final Processor processor, final ProcessorRecord record) {
        record.from(processor);
        record.set(PROCESSOR.ID, processor.getId());
        record.set(PROCESSOR.VERSION, processor.getVersion());
        record.set(PROCESSOR.CREATE_TIME_MS, processor.getCreateTimeMs());
        record.set(PROCESSOR.CREATE_USER, processor.getCreateUser());
        record.set(PROCESSOR.UPDATE_TIME_MS, processor.getUpdateTimeMs());
        record.set(PROCESSOR.UPDATE_USER, processor.getUpdateUser());
        record.set(PROCESSOR.UUID, processor.getUuid());
        record.set(PROCESSOR.PIPELINE_UUID, processor.getUuid());
        record.set(PROCESSOR.TASK_TYPE, processor.getProcessorType().getDisplayValue());
        record.set(PROCESSOR.ENABLED, processor.isEnabled());
        record.set(PROCESSOR.DELETED, processor.isDeleted());
        return record;
    }
}
