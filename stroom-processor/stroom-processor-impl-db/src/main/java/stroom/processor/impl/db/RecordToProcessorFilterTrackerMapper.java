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

package stroom.processor.impl.db;

import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;

import org.jooq.Record;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

class RecordToProcessorFilterTrackerMapper implements Function<Record, ProcessorFilterTracker> {

    @Override
    public ProcessorFilterTracker apply(final Record record) {
        return ProcessorFilterTracker
                .builder()
                .id(record.get(PROCESSOR_FILTER_TRACKER.ID))
                .version(record.get(PROCESSOR_FILTER_TRACKER.VERSION))
                .minMetaId(record.get(PROCESSOR_FILTER_TRACKER.MIN_META_ID))
                .minEventId(record.get(PROCESSOR_FILTER_TRACKER.MIN_EVENT_ID))
                .maxMetaCreateMs(record.get(PROCESSOR_FILTER_TRACKER.MAX_META_CREATE_MS))
                .minMetaCreateMs(record.get(PROCESSOR_FILTER_TRACKER.MIN_META_CREATE_MS))
                .metaCreateMs(record.get(PROCESSOR_FILTER_TRACKER.META_CREATE_MS))
                .lastPollMs(record.get(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS))
                .lastPollTaskCount(record.get(PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT))
                .status(ProcessorFilterTrackerStatus.PRIMITIVE_VALUE_CONVERTER
                        .fromPrimitiveValue(
                                record.get(PROCESSOR_FILTER_TRACKER.STATUS),
                                ProcessorFilterTrackerStatus.CREATED))
                .message(record.get(PROCESSOR_FILTER_TRACKER.MESSAGE))
                .metaCount(record.get(PROCESSOR_FILTER_TRACKER.META_COUNT))
                .eventCount(record.get(PROCESSOR_FILTER_TRACKER.EVENT_COUNT))
                .build();
    }
}
