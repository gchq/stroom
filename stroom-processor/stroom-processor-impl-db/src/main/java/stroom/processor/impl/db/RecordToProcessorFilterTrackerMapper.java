package stroom.processor.impl.db;

import org.jooq.Record;
import stroom.processor.shared.ProcessorFilterTracker;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

class RecordToProcessorFilterTrackerMapper implements Function<Record, ProcessorFilterTracker> {
    @Override
    public ProcessorFilterTracker apply(final Record record) {
        final ProcessorFilterTracker processorFilterTracker = new ProcessorFilterTracker();
        processorFilterTracker.setId(record.get(PROCESSOR_FILTER_TRACKER.ID));
        processorFilterTracker.setVersion(record.get(PROCESSOR_FILTER_TRACKER.VERSION));
        processorFilterTracker.setMinMetaId(record.get(PROCESSOR_FILTER_TRACKER.MIN_META_ID));
        processorFilterTracker.setMinEventId(record.get(PROCESSOR_FILTER_TRACKER.MIN_EVENT_ID));
        processorFilterTracker.setMaxMetaCreateMs(record.get(PROCESSOR_FILTER_TRACKER.MAX_META_CREATE_MS));
        processorFilterTracker.setMinMetaCreateMs(record.get(PROCESSOR_FILTER_TRACKER.MIN_META_CREATE_MS));
        processorFilterTracker.setMetaCreateMs(record.get(PROCESSOR_FILTER_TRACKER.META_CREATE_MS));
        processorFilterTracker.setLastPollMs(record.get(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS));
        processorFilterTracker.setLastPollTaskCount(record.get(PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT));
        processorFilterTracker.setStatus(record.get(PROCESSOR_FILTER_TRACKER.STATUS));
        processorFilterTracker.setMetaCount(record.get(PROCESSOR_FILTER_TRACKER.META_COUNT));
        processorFilterTracker.setEventCount(record.get(PROCESSOR_FILTER_TRACKER.EVENT_COUNT));
        return processorFilterTracker;
    }
}
