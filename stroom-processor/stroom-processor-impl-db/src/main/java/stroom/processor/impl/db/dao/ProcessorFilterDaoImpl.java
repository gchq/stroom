package stroom.processor.impl.db.dao;

import stroom.entity.shared.BaseResultList;
import stroom.persist.ConnectionProvider;
import stroom.processor.impl.db.tables.records.ProcessorFilterRecord;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.jooq.JooqUtil;

import static stroom.processor.impl.db.tables.ProcessorFilter.PROCESSOR_FILTER;

public class ProcessorFilterDaoImpl implements ProcessorFilterDao {

    private final ConnectionProvider connectionProvider;

    ProcessorFilterDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public ProcessorFilter create() {
        ProcessorFilter processorFilter = new ProcessorFilter();

        return JooqUtil.contextResult(connectionProvider, context -> {
            final ProcessorFilterRecord processorFilterRecord = context.newRecord(PROCESSOR_FILTER, processorFilter);
            processorFilterRecord.store();
            return processorFilterRecord.into(ProcessorFilter.class);
        });
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        // TODO FK relationship
        return JooqUtil.contextResultWithOptimisticLocking(connectionProvider, context -> {
            final ProcessorFilterRecord processorFilterRecord =
                    context.newRecord(PROCESSOR_FILTER, processorFilter);
            processorFilterRecord.update();
            return processorFilterRecord.into(ProcessorFilter.class);
        });
    }

    @Override
    public int delete(final int id) {
        return JooqUtil.deleteById(connectionProvider, PROCESSOR_FILTER, PROCESSOR_FILTER.ID, id);
    }

    @Override
    public ProcessorFilter fetch(final int id) {
        // TODO FK relationship
        return JooqUtil.fetchById(connectionProvider, PROCESSOR_FILTER, PROCESSOR_FILTER.ID, ProcessorFilter.class, id);
    }

    @Override
    public BaseResultList<ProcessorFilter> find(final FindStreamProcessorFilterCriteria criteria) {
        return null;
    }
}
