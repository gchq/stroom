package stroom.processor.impl.db;

import org.jooq.Record;
import stroom.processor.shared.Processor;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;

class RecordToProcessorMapper implements Function<Record, Processor> {
    @Override
    public Processor apply(final Record record) {
        final Processor processor = new Processor();
        processor.setId(record.get(PROCESSOR.ID));
        processor.setVersion(record.get(PROCESSOR.VERSION));
        processor.setCreateTimeMs(record.get(PROCESSOR.CREATE_TIME_MS));
        processor.setCreateUser(record.get(PROCESSOR.CREATE_USER));
        processor.setUpdateTimeMs(record.get(PROCESSOR.UPDATE_TIME_MS));
        processor.setUpdateUser(record.get(PROCESSOR.UPDATE_USER));
        processor.setUuid(record.get(PROCESSOR.UUID));
        processor.setPipelineUuid(record.get(PROCESSOR.PIPELINE_UUID));
        processor.setTaskType(record.get(PROCESSOR.TASK_TYPE));
        processor.setEnabled(record.get(PROCESSOR.ENABLED));
        return processor;
    }
}
