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
        processorFilterTracker.setVersion(record.get(PROCESSOR_FILTER_TRACKER.VERSION).intValue()); // TODO : @66 Remove when version is an integer
        processorFilterTracker.setMinStreamId(record.get(PROCESSOR_FILTER_TRACKER.MIN_STREAM_ID));
        processorFilterTracker.setMinEventId(record.get(PROCESSOR_FILTER_TRACKER.MIN_EVENT_ID));
        processorFilterTracker.setMaxStreamCreateMs(record.get(PROCESSOR_FILTER_TRACKER.MAX_STREAM_CREATE_MS));
        processorFilterTracker.setMinStreamCreateMs(record.get(PROCESSOR_FILTER_TRACKER.MIN_STREAM_CREATE_MS));
        processorFilterTracker.setStreamCreateMs(record.get(PROCESSOR_FILTER_TRACKER.STREAM_CREATE_MS));
        processorFilterTracker.setLastPollMs(record.get(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS));
        processorFilterTracker.setLastPollTaskCount(record.get(PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT));
        processorFilterTracker.setStatus(record.get(PROCESSOR_FILTER_TRACKER.STATUS));
        processorFilterTracker.setStreamCount(record.get(PROCESSOR_FILTER_TRACKER.STREAM_COUNT));
        processorFilterTracker.setEventCount(record.get(PROCESSOR_FILTER_TRACKER.EVENT_COUNT));
        return processorFilterTracker;
    }
}
