package stroom.processor.impl.db;

import stroom.db.util.GenericDao;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterTrackerRecord;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jooq.DSLContext;

import java.util.Optional;
import javax.inject.Inject;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

class ProcessorFilterTrackerDaoImpl implements ProcessorFilterTrackerDao {

    private static final LambdaLogger LAMBDA_LOGGER =
            LambdaLoggerFactory.getLogger(ProcessorFilterTrackerDaoImpl.class);

    private final GenericDao<ProcessorFilterTrackerRecord, ProcessorFilterTracker, Integer> genericDao;

    @Inject
    ProcessorFilterTrackerDaoImpl(final ProcessorDbConnProvider processorDbConnProvider) {
        this.genericDao = new GenericDao<>(
                processorDbConnProvider,
                PROCESSOR_FILTER_TRACKER,
                PROCESSOR_FILTER_TRACKER.ID,
                ProcessorFilterTracker.class);
    }

    @Override
    public ProcessorFilterTracker create(final ProcessorFilterTracker processorFilterTracker) {
        return genericDao.create(processorFilterTracker);
    }

    @Override
    public Optional<ProcessorFilterTracker> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public ProcessorFilterTracker update(final ProcessorFilterTracker processorFilterTracker) {
        return genericDao.update(processorFilterTracker);
    }

    public ProcessorFilterTracker update(final DSLContext context,
                                         final ProcessorFilterTracker processorFilterTracker) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Updating a {} with id {}",
                PROCESSOR_FILTER_TRACKER.getName(),
                processorFilterTracker.getId()));
        ProcessorFilterTrackerRecord record = context.newRecord(PROCESSOR_FILTER_TRACKER);
        record.from(processorFilterTracker);
        record.update();
        return record.into(ProcessorFilterTracker.class);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }
}
