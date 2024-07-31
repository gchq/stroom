/*
 * Copyright 2024 Crown Copyright
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

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterRecord;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterTrackerRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class ProcessorFilterDaoImpl implements ProcessorFilterDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorFilterDaoImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterDaoImpl.class);

    private static final Map<CIKey, Field<?>> FIELD_MAP = CIKey.mapOf(
            ProcessorFilterFields.FIELD_ID, PROCESSOR_FILTER.ID);

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER =
            new RecordToProcessorFilterMapper();
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER =
            new RecordToProcessorFilterTrackerMapper();

    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ProcessorFilterMarshaller marshaller;
    private final ExpressionMapper expressionMapper;
    private final ProcessorFilterTrackerDaoImpl processorFilterTrackerDaoImpl;

    @Inject
    ProcessorFilterDaoImpl(final ProcessorDbConnProvider processorDbConnProvider,
                           final ExpressionMapperFactory expressionMapperFactory,
                           final ProcessorFilterMarshaller marshaller,
                           final ProcessorFilterTrackerDaoImpl processorFilterTrackerDaoImpl) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.marshaller = marshaller;
        this.processorFilterTrackerDaoImpl = processorFilterTrackerDaoImpl;

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorFilterFields.ID, PROCESSOR_FILTER.ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.LAST_POLL_MS, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, Long::valueOf);
        expressionMapper.map(ProcessorFilterFields.PRIORITY, PROCESSOR_FILTER.PRIORITY, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.ENABLED, PROCESSOR_FILTER.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.DELETED, PROCESSOR_FILTER.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_ID, PROCESSOR_FILTER.FK_PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.UUID, PROCESSOR_FILTER.UUID, value -> value);

        expressionMapper.map(ProcessorFields.ID, PROCESSOR.ID, Integer::valueOf);
        expressionMapper.map(ProcessorFields.PROCESSOR_TYPE, PROCESSOR.TASK_TYPE, String::valueOf);
        expressionMapper.map(ProcessorFields.ANALYTIC_RULE, PROCESSOR.PIPELINE_UUID, value -> value, false);
        expressionMapper.map(ProcessorFields.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value, false);
        expressionMapper.map(ProcessorFields.ENABLED, PROCESSOR.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFields.DELETED, PROCESSOR.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFields.UUID, PROCESSOR.UUID, value -> value);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", PROCESSOR_FILTER.getName()));

        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();

        final ProcessorFilterTrackerRecord processorFilterTrackerRecord = PROCESSOR_FILTER_TRACKER.newRecord();
        processorFilterTrackerRecord.from(tracker);

        final ProcessorFilterRecord processorFilterRecord = PROCESSOR_FILTER.newRecord();
        processorFilterRecord.from(marshalled);
        processorFilterRecord.setFkProcessorId(marshalled.getProcessor().getId());

        final Tuple2<ProcessorFilterRecord, ProcessorFilterTracker> result = JooqUtil.transactionResult(
                processorDbConnProvider,
                context -> {
                    processorFilterTrackerRecord.attach(context.configuration());
                    processorFilterTrackerRecord.store();

                    // The .store() doesn't seem to bring back the default value for status so re-fetch
                    final ProcessorFilterTracker persistedTracker = processorFilterTrackerDaoImpl.fetch(
                                    context, processorFilterTrackerRecord.getId())
                            .orElseThrow(() -> new RuntimeException(
                                    "Can't find newly created tracker with id" + processorFilterTrackerRecord.getId()));

                    marshalled.setProcessorFilterTracker(persistedTracker);
                    processorFilterRecord.setFkProcessorFilterTrackerId(persistedTracker.getId());

                    processorFilterRecord.attach(context.configuration());
                    processorFilterRecord.store();

                    return Tuple.of(processorFilterRecord, persistedTracker);
                });

        final ProcessorFilterRecord processorFilterRecord2 = result._1;
        final ProcessorFilterTracker processorFilterTracker = result._2;
        final ProcessorFilter processorFilter2 = processorFilterRecord2.into(ProcessorFilter.class);
        processorFilter2.setProcessorFilterTracker(processorFilterTracker);
        processorFilter2.setProcessor(processorFilter.getProcessor());

        return marshaller.unmarshal(processorFilter2);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        final ProcessorFilterRecord record = PROCESSOR_FILTER.newRecord();
        record.from(processorFilter);
        final ProcessorFilterRecord persistedRecord = JooqUtil.updateWithOptimisticLocking(processorDbConnProvider,
                record);
        final ProcessorFilter result = persistedRecord.into(ProcessorFilter.class);
        result.setProcessorFilterTracker(marshalled.getProcessorFilterTracker());
        result.setProcessor(marshalled.getProcessor());
        return marshaller.unmarshal(result);
    }

    @Override
    public boolean delete(final int id) {
        // We don't want to allow direct physical delete, only logical delete.
        return logicalDeleteByProcessorFilterId(id) > 0;
        //genericDao.delete(id);
    }

    @Override
    public int logicalDeleteByProcessorFilterId(final int processorFilterId) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicalDeleteByProcessorFilterId(processorFilterId, context));
    }

    public int logicalDeleteByProcessorFilterId(final int processorFilterId, final DSLContext context) {
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.ID.eq(processorFilterId))
                .and(PROCESSOR_FILTER.DELETED.eq(false))
                .execute();
        LOGGER.debug("Logically deleted {} processor filters for processor filter Id {}",
                count,
                processorFilterId);
        return count;
    }

    public int logicalDeleteByProcessorId(final int processorId) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicalDeleteByProcessorId(processorId, context));
    }

    public int logicalDeleteByProcessorId(final int processorId, final DSLContext context) {
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(processorId))
                .and(PROCESSOR_FILTER.DELETED.eq(false))
                .execute();
        LOGGER.debug("Logically deleted {} processor filters for processor Id {}",
                count,
                processorId);
        return count;
    }

    /**
     * Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
     * than the threshold. Note that COMPLETE just means that we have finished producing tasks on the DB, but we
     * can't delete the filter until all associated tasks have been processed otherwise they will never be picked
     * up.
     *
     * @param deleteThreshold Only logically delete filters with a last poll time older than the threshold.
     * @return The number of logically deleted filters.
     */
    @Override
    public int logicallyDeleteOldProcessorFilters(final Instant deleteThreshold) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicallyDeleteOldFilters(deleteThreshold, context));
    }

    public int logicallyDeleteOldFilters(final Instant deleteThreshold, final DSLContext context) {
        var query = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.DELETED.eq(false))
                .and(DSL.exists(
                        DSL.selectZero()
                                .from(PROCESSOR_FILTER_TRACKER)
                                .where(PROCESSOR_FILTER_TRACKER.ID.eq(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID))
                                .and(PROCESSOR_FILTER_TRACKER.STATUS.eq(
                                        ProcessorFilterTrackerStatus.COMPLETE.getPrimitiveValue()))
                                .and(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS.lessThan(deleteThreshold.toEpochMilli()))))
                .and(DSL.notExists(
                        DSL.selectZero()
                                .from(PROCESSOR_TASK)
                                .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))));

        LOGGER.trace("logicallyDeleteOldFilters query:\n{}", query);
        final int count = query.execute();

        LOGGER.debug("Logically deleted {} processor filters with a state of COMPLETE with no outstanding tasks and " +
                        "last poll before {}",
                count, deleteThreshold);
        return count;
    }

    /**
     * Physically delete old processor filters that are logically deleted with an update time older than the threshold.
     *
     * @param deleteThreshold Only physically delete filters with an update time older than the threshold.
     * @return The processor filter UUIDs of all the processor filters deleted.
     */
    @Override
    public Set<String> physicalDeleteOldProcessorFilters(final Instant deleteThreshold) {
        final List<PipeFilterKeys> result =
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(PROCESSOR_FILTER.ID,
                                PROCESSOR_FILTER.UUID,
                                PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID)
                        .from(PROCESSOR_FILTER)
                        .where(PROCESSOR_FILTER.DELETED.eq(true))
                        .and(PROCESSOR_FILTER.UPDATE_TIME_MS.lessThan(deleteThreshold.toEpochMilli()))
                        .fetch()
                        .map(rec -> new PipeFilterKeys(
                                rec.get(PROCESSOR_FILTER.ID),
                                rec.get(PROCESSOR_FILTER.UUID),
                                rec.get(PROCESSOR_FILTER_TRACKER.ID))));

        LAMBDA_LOGGER.debug(() ->
                LogUtil.message("Found {} logically deleted filters with an update time older than {}",
                        result.size(), deleteThreshold));

        final Set<String> processorFilterUuids = new HashSet<>();
        // Delete one by one as we expect some constraint errors.
        result.forEach(dbKeys -> {
            try {
                final int count = JooqUtil.transactionResult(processorDbConnProvider, context -> {
                    final int filterCount = context
                            .deleteFrom(PROCESSOR_FILTER)
                            .where(PROCESSOR_FILTER.ID.eq(dbKeys.processorFilterId))
                            .execute();
                    LOGGER.debug("Physically deleted {} processor filters with id {}",
                            filterCount,
                            dbKeys.processorFilterId);

                    final int trackerCount = context
                            .deleteFrom(PROCESSOR_FILTER_TRACKER)
                            .where(PROCESSOR_FILTER_TRACKER.ID.eq(dbKeys.processorFilterTrackerId))
                            .execute();
                    LOGGER.debug("Physically deleted {} processor filter trackers with id {}",
                            trackerCount, dbKeys.processorFilterTrackerId);

                    return filterCount;
                });

                if (count > 0) {
                    // Make a note of the uuid, so we can delete any doc perms associated with it
                    // The tracker is 1:1 with filter, so don't need to return tracker delete count
                    processorFilterUuids.add(dbKeys.processorFilterUuid);
                }
            } catch (final DataAccessException e) {
                if (e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx) {
                    LOGGER.debug("Expected constraint violation exception: " + sqlEx.getMessage(), e);
                } else {
                    throw e;
                }
            }
        });
        LAMBDA_LOGGER.debug(() -> "physicalDeleteOldProcessorFilters returning: "
                + processorFilterUuids.size() + " UUIDs");
        return processorFilterUuids;
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                        context
                                .select()
                                .from(PROCESSOR_FILTER)
                                .join(PROCESSOR_FILTER_TRACKER)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                                .join(PROCESSOR)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                                .where(PROCESSOR_FILTER.ID.eq(id))
                                .fetchOptional())
                .map(this::mapRecord);
    }

    @Override
    public Optional<ProcessorFilter> fetchByUuid(final String uuid) {
        Objects.requireNonNull(uuid);
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                        context
                                .select()
                                .from(PROCESSOR_FILTER)
                                .join(PROCESSOR_FILTER_TRACKER)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                                .join(PROCESSOR)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                                .where(PROCESSOR_FILTER.UUID.eq(uuid))
                                .fetchOptional())
                .map(this::mapRecord);
    }

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<ProcessorFilter> list = JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select()
                        .from(PROCESSOR_FILTER)
                        .join(PROCESSOR_FILTER_TRACKER)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                        .join(PROCESSOR)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                        .where(condition)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(this::mapRecord);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private ProcessorFilter mapRecord(final Record record) {
        final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
        final ProcessorFilter processorFilter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
        final ProcessorFilterTracker processorFilterTracker =
                RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);
        processorFilter.setProcessor(processor);
        processorFilter.setProcessorFilterTracker(processorFilterTracker);
        return marshaller.unmarshal(processorFilter);
    }


    // --------------------------------------------------------------------------------


    private record PipeFilterKeys(
            int processorFilterId,
            String processorFilterUuid,
            int processorFilterTrackerId) {

    }
}
