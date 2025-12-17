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

import stroom.db.util.JooqUtil;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.Optional;
import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

class ProcessorFilterTrackerDaoImpl implements ProcessorFilterTrackerDao {

    private static final LambdaLogger LAMBDA_LOGGER =
            LambdaLoggerFactory.getLogger(ProcessorFilterTrackerDaoImpl.class);

    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER =
            new RecordToProcessorFilterTrackerMapper();

    private final ProcessorDbConnProvider processorDbConnProvider;

    @Inject
    ProcessorFilterTrackerDaoImpl(final ProcessorDbConnProvider processorDbConnProvider) {
        this.processorDbConnProvider = processorDbConnProvider;
    }

    @Override
    public Optional<ProcessorFilterTracker> fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                        context
                                .select()
                                .from(PROCESSOR_FILTER_TRACKER)
                                .where(PROCESSOR_FILTER_TRACKER.ID.eq(id))
                                .fetchOptional())
                .map(RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER);
    }

    public Optional<ProcessorFilterTracker> fetch(final DSLContext context, final int id) {
        return context
                .select()
                .from(PROCESSOR_FILTER_TRACKER)
                .where(PROCESSOR_FILTER_TRACKER.ID.eq(id))
                .fetchOptional()
                .map(RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER);
    }

    @Override
    public int update(final ProcessorFilterTracker processorFilterTracker) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                update(context, processorFilterTracker));
    }

    public int update(final DSLContext context,
                      final ProcessorFilterTracker processorFilterTracker) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Updating a {} with id {}",
                PROCESSOR_FILTER_TRACKER.getName(),
                processorFilterTracker.getId()));
        final int count = context
                .update(PROCESSOR_FILTER_TRACKER)
                .set(PROCESSOR_FILTER_TRACKER.VERSION, PROCESSOR_FILTER_TRACKER.VERSION.plus(1))
                .set(PROCESSOR_FILTER_TRACKER.MIN_META_ID, processorFilterTracker.getMinMetaId())
                .set(PROCESSOR_FILTER_TRACKER.MIN_EVENT_ID, processorFilterTracker.getMinEventId())
                .set(PROCESSOR_FILTER_TRACKER.MAX_META_CREATE_MS, processorFilterTracker.getMaxMetaCreateMs())
                .set(PROCESSOR_FILTER_TRACKER.MIN_META_CREATE_MS, processorFilterTracker.getMinMetaCreateMs())
                .set(PROCESSOR_FILTER_TRACKER.META_CREATE_MS, processorFilterTracker.getMetaCreateMs())
                .set(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, processorFilterTracker.getLastPollMs())
                .set(PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT, processorFilterTracker.getLastPollTaskCount())
                .set(PROCESSOR_FILTER_TRACKER.STATUS, processorFilterTracker.getStatus().getPrimitiveValue())
                .set(PROCESSOR_FILTER_TRACKER.MESSAGE, processorFilterTracker.getMessage())
                .set(PROCESSOR_FILTER_TRACKER.META_COUNT, processorFilterTracker.getMetaCount())
                .set(PROCESSOR_FILTER_TRACKER.EVENT_COUNT, processorFilterTracker.getEventCount())
                .where(PROCESSOR_FILTER_TRACKER.ID.eq(processorFilterTracker.getId()))
                .and(PROCESSOR_FILTER_TRACKER.VERSION.eq(processorFilterTracker.getVersion()))
                .execute();

        if (count == 0) {
            throw new RuntimeException("Unable to update tracker with id = " + processorFilterTracker.getId());
        }

        return count;
    }
}
