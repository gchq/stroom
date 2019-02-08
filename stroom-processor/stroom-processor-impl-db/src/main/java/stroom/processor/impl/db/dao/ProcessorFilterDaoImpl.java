package stroom.processor.impl.db.dao;

import org.jooq.impl.DSL;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.persist.ConnectionProvider;
import stroom.processor.impl.db.StreamProcessorFilterMarshaller;
import stroom.processor.impl.db.tables.records.ProcessorFilterRecord;
import stroom.processor.impl.db.tables.records.ProcessorRecord;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.QueryData;

import java.util.Optional;

import static stroom.processor.impl.db.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.tables.ProcessorFilter.PROCESSOR_FILTER;

public class ProcessorFilterDaoImpl implements ProcessorFilterDao {

    private final ConnectionProvider connectionProvider;
    private final GenericDao<ProcessorFilterRecord, ProcessorFilter, Integer> delegateDao;
    private final StreamProcessorFilterMarshaller marshaller;

    ProcessorFilterDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
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
            processorFilterRecord.update();
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
        return JooqUtil.fetchById(connectionProvider, PROCESSOR_FILTER, ProcessorFilter.class, id);
    }

    @Override
    public BaseResultList<ProcessorFilter> find(final FindStreamProcessorFilterCriteria criteria) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public ProcessorFilter createNewFilter(
            final DocRef pipelineRef,
            final QueryData queryData,
            final boolean enabled,
            final int priority) {

        JooqUtil.contextWithOptimisticLocking(connectionProvider, dslContext -> {
            return dslContext.transaction(configuration -> {

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
                    processor = processorRecord.into(Processor.class);
                }


                ProcessorFilter filter = new ProcessorFilter();
                // Blank tracker
                filter.setEnabled(enabled);
                filter.setPriority(priority);
                filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
                filter.setStreamProcessor(processor);
                filter.setQueryData(queryData);
                filter = marshaller.marshal(filter);
                // Save initial tracker





                return null;
            });
        });


        // First see if we can find a stream processor for this pipeline.
        final FindStreamProcessorCriteria findStreamProcessorCriteria = new FindStreamProcessorCriteria(pipelineRef);
        final List<Processor> list = streamProcessorService.find(findStreamProcessorCriteria);
        Processor processor;
        if (list == null || list.size() == 0) {
            // We couldn't find one so create a new one.
            processor = new Processor(pipelineRef);
            processor.setEnabled(enabled);
            processor = streamProcessorService.save(processor);
        } else {
            processor = list.get(0);
        }

        ProcessorFilter filter = new ProcessorFilter();
        // Blank tracker
        filter.setEnabled(enabled);
        filter.setPriority(priority);
        filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
        filter.setStreamProcessor(processor);
        filter.setQueryData(queryData);
        filter = marshaller.marshal(filter);
        // Save initial tracker
        getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
        getEntityManager().flush();
        filter = save(filter);
        filter = marshaller.unmarshal(filter);

        return filter;
    }


}
