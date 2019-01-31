package stroom.job.impl.db;

import stroom.job.api.Job;
import stroom.job.impl.db.stroom.tables.records.JobRecord;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.stroom.Tables.JOB;

class JobDaoImpl implements JobDao {

    private final ConnectionProvider connectionProvider;
    private GenericDao<JobRecord, Job> dao;

    @Inject
    JobDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        dao = new GenericDao(JOB, JOB.ID, Job.class, connectionProvider);
    }

    @Override
    public Job create(final Job job) {
        return dao.create(job);
    }

    @Override
    public Job create() {
        throw new RuntimeException("Not implemented yet -- interface inappropriate for use with GenericDao");
    }

    @Override
    public Job update(final Job job) {
        return dao.update(job);
    }

    @Override
    public int delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<Job> fetch(int id) {
        return dao.fetch(id);
    }

}
