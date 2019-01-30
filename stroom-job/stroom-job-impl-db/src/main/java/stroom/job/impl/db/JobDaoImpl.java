package stroom.job.impl.db;

import stroom.job.impl.db.stroom.tables.records.JobRecord;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.stroom.Tables.JOB;

/**
 * This class is very slim because it uses the GenericDao.
 * Why event use this class? Why not use the GenericDao directly in the service class?
 * Some reasons:
 * 1. Hides knowledge of Jooq classes from the service
 * 2. Hides connection provider and GenericDao instantiation -- the service class just gets a working thing injected.
 * 3. It allows the DAO to be easily extended.
 *
 * //TODO gh-1072 Maybe the interface could implement the standard methods below? Then this would be even slimmer.
 */
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
