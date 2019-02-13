package stroom.processor.impl.db.dao;

import org.jooq.Configuration;
import org.jooq.impl.DSL;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.persist.ConnectionProvider;
import stroom.processor.impl.db.StreamProcessorFilterMarshaller;
import stroom.processor.impl.db.tables.records.ProcessorFilterRecord;
import stroom.processor.impl.db.tables.records.ProcessorFilterTrackerRecord;
import stroom.processor.impl.db.tables.records.ProcessorRecord;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.QueryData;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.processor.impl.db.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

public class ProcessorFilterDaoImpl implements ProcessorFilterDao {

    private final ConnectionProvider connectionProvider;
    private final GenericDao<ProcessorFilterRecord, ProcessorFilter, Integer> delegateDao;
    private final StreamProcessorFilterMarshaller marshaller;

    @Inject
    ProcessorFilterDaoImpl(final ConnectionProvider connectionProvider,
                           final StreamProcessorFilterMarshaller marshaller) {
        this.connectionProvider = connectionProvider;
        this.marshaller = marshaller;
        this.delegateDao = new GenericDao<>(
                PROCESSOR_FILTER, PROCESSOR_FILTER.ID, ProcessorFilter.class, connectionProvider);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        return delegateDao.create(processorFilter);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        // TODO FK relationship
        return JooqUtil.contextWithOptimisticLocking(connectionProvider, context -> {
            final ProcessorFilterRecord processorFilterRecord =
                    context.newRecord(PROCESSOR_FILTER, processorFilter);

            processorFilterRecord
                    .setFkProcessorFilterTrackerId(processorFilter.getStreamProcessorFilterTracker().getId());
            processorFilterRecord
                    .setFkProcessorId(processorFilter.getStreamProcessor().getId());

            // persist it then read it back
            processorFilterRecord.store();
            return processorFilterRecord.into(ProcessorFilter.class);
        });
    }

    @Override
    public boolean delete(final int id) {
        return delegateDao.delete(id);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        // TODO FK relationship
        return JooqUtil.contextResult(connectionProvider, context ->
                context
                        .select()
                        .from(PROCESSOR_FILTER)
                        .join(PROCESSOR_FILTER_TRACKER)
                            .onKey()
                        .join(PROCESSOR)
                            .onKey()
                        .where(PROCESSOR_FILTER.ID.eq(id))
                        .fetchOptional()
                        .map(record -> {
                            // TODO can you map multiple pojos like this?
                            // Order may be key here if it matches by field name
                            final ProcessorFilter filter = record.into(ProcessorFilter.class);
                            final ProcessorFilterTracker tracker = record.into(ProcessorFilterTracker.class);
                            final Processor processor = record.into(Processor.class);

                            filter.setStreamProcessor(processor);
                            filter.setStreamProcessorFilterTracker(tracker);
                            return filter;
                        }));
    }

    @Override
    public BaseResultList<ProcessorFilter> find(final FindStreamProcessorFilterCriteria criteria) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public ProcessorFilter createFilter(
            final DocRef pipelineRef,
            final QueryData queryData,
            final boolean enabled,
            final int priority) {

        return JooqUtil.contextWithOptimisticLocking(connectionProvider, dslContext -> {
            return dslContext.transactionResult(configuration -> {

                Processor processor;
                processor = DSL.using(configuration)
                        .selectFrom(PROCESSOR)
                        .where(PROCESSOR.PIPELINE_UUID.eq(pipelineRef.getUuid()))
                        .fetchOneInto(Processor.class);

                if (processor == null) {
                    processor = new Processor();
                    processor.setEnabled(enabled);

                    // persist the new processor
                    ProcessorRecord processorRecord = DSL.using(configuration)
                            .newRecord(PROCESSOR, processor);

                    processorRecord.store();
                    // read the record back into the pojo
                    processor = processorRecord.into(Processor.class);
                }
                return createFilter(configuration, processor, queryData, enabled, priority);

            });
        });
    }

    @Override
    public ProcessorFilter createFilter(final Processor processor,
                                        final QueryData queryData,
                                        final boolean enabled,
                                        final int priority) {

        return JooqUtil.contextWithOptimisticLocking(connectionProvider, dslContext -> {
            return dslContext.transactionResult(configuration ->
                    createFilter(configuration, processor, queryData, enabled, priority));
        });
    }

    private ProcessorFilter createFilter( final Configuration configuration,
                                          final Processor processor,
                                          final QueryData queryData,
                                          final boolean enabled,
                                          final int priority) {

        // now create the filter and tracker
        ProcessorFilter filter = new ProcessorFilter();
        ProcessorFilterTracker tracker = new ProcessorFilterTracker();
        // Blank tracker
        filter.setEnabled(enabled);
        filter.setPriority(priority);
        filter.setStreamProcessorFilterTracker(tracker);
        filter.setStreamProcessor(processor);
        filter.setQueryData(queryData);
        filter = marshaller.marshal(filter);

        // Save initial tracker
        ProcessorFilterTrackerRecord processorFilterTrackerRecord = DSL.using(configuration)
                .newRecord(PROCESSOR_FILTER_TRACKER, tracker);
        processorFilterTrackerRecord.store();

        ProcessorFilterRecord processorFilterRecord = DSL.using(configuration)
                .newRecord(PROCESSOR_FILTER, filter);

        processorFilterRecord.store();

        // read the filter pojo back from the persisted record
        filter = processorFilterRecord.into(ProcessorFilter.class);
        filter = marshaller.marshal(filter);
        return filter;
    }

}
