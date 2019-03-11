package stroom.processor.impl.db;

import org.jooq.DSLContext;
import stroom.db.util.GenericDao;
import stroom.persist.ConnectionProvider;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterTaskRecord;
import stroom.processor.shared.ProcessorFilterTask;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTask.PROCESSOR_FILTER_TASK;

class ProcessorFilterTaskDaoImpl implements ProcessorFilterTaskDao {
    private final GenericDao<ProcessorFilterTaskRecord, ProcessorFilterTask, Long> dao;

    @Inject
    ProcessorFilterTaskDaoImpl(final ConnectionProvider connectionProvider,
                               final ProcessorNodeCache processorNodeCache) {
        this.dao = new GenericDao<>(PROCESSOR_FILTER_TASK, PROCESSOR_FILTER_TASK.ID, ProcessorFilterTask.class, connectionProvider);
        this.dao.setObjectToRecordMapper((processorFilterTask, record) -> {
            record.from(processorFilterTask);
            record.set(PROCESSOR_FILTER_TASK.FK_PROCESSOR_FILTER_ID, processorFilterTask.getProcessorFilter().getId());
            record.set(PROCESSOR_FILTER_TASK.FK_PROCESSOR_NODE_ID, processorNodeCache.getOrCreate(processorFilterTask.getNodeName()));
            return record;
        });
    }

    @Override
    public Optional<ProcessorFilterTask> fetch(final DSLContext context, final ProcessorFilterTask processorFilterTask) {
        return dao.fetch(context, processorFilterTask.getId()).map(p -> decorate(p, processorFilterTask));
    }

    @Override
    public ProcessorFilterTask update(final DSLContext context, final ProcessorFilterTask processorFilterTask) {
        return decorate(dao.update(context, processorFilterTask), processorFilterTask);
    }

    private ProcessorFilterTask decorate(final ProcessorFilterTask result, final ProcessorFilterTask original) {
        result.setNodeName(original.getNodeName());
        result.setProcessorFilter(original.getProcessorFilter());
        return result;
    }
}
