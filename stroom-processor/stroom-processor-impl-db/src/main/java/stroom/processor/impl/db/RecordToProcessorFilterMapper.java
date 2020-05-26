package stroom.processor.impl.db;

import stroom.processor.shared.ProcessorFilter;

import org.jooq.Record;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;

class RecordToProcessorFilterMapper implements Function<Record, ProcessorFilter> {
    @Override
    public ProcessorFilter apply(final Record record) {
        final ProcessorFilter processorFilter = new ProcessorFilter();
        processorFilter.setId(record.get(PROCESSOR_FILTER.ID));
        processorFilter.setVersion(record.get(PROCESSOR_FILTER.VERSION));
        processorFilter.setCreateTimeMs(record.get(PROCESSOR_FILTER.CREATE_TIME_MS));
        processorFilter.setCreateUser(record.get(PROCESSOR_FILTER.CREATE_USER));
        processorFilter.setUpdateTimeMs(record.get(PROCESSOR_FILTER.UPDATE_TIME_MS));
        processorFilter.setUpdateUser(record.get(PROCESSOR_FILTER.UPDATE_USER));
        processorFilter.setUuid(record.get(PROCESSOR_FILTER.UUID));
        processorFilter.setData(record.get(PROCESSOR_FILTER.DATA));
        processorFilter.setPriority(record.get(PROCESSOR_FILTER.PRIORITY));
        processorFilter.setReprocess(record.get(PROCESSOR_FILTER.REPROCESS));
        processorFilter.setEnabled(record.get(PROCESSOR_FILTER.ENABLED));
        processorFilter.setDeleted(record.get(PROCESSOR_FILTER.DELETED));
        return processorFilter;
    }
}
