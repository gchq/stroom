package stroom.job.impl.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.job.impl.db.stroom.tables.records.JobRecord;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.stroom.Tables.JOB;
import static stroom.util.jooq.JooqUtil.contextWithOptimisticLocking;

class JobDaoImpl implements JobDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    @Inject
    JobDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Job create(final Job job) {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            JobRecord jobRecord = context.newRecord(JOB, job);
            jobRecord.store();
            Job createdJob = jobRecord.into(Job.class);
            return createdJob;
        });
    }

    @Override
    public Job create() {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            JobRecord jobRecord = context.newRecord(JOB, new Job());
            jobRecord.store();
            Job createdJob = jobRecord.into(Job.class);
            return createdJob;
        });
    }

    @Override
    public Job update(final Job job) {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            JobRecord jobRecord = context.newRecord(JOB, job);
            jobRecord.update();
            return jobRecord.into(Job.class);
        });
    }

    @Override
    public int delete(int id) {
        return contextWithOptimisticLocking(connectionProvider, context -> {
            return context
                    .deleteFrom(JOB)
                    .where(JOB.ID.eq(id))
                    .execute();
        });
    }

    @Override
    public Optional<Job> fetch(int id) {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            Job job = context.selectFrom(JOB).where(JOB.ID.eq(id)).fetchOneInto(Job.class);
            return Optional.ofNullable(job);
        });
    }

}
