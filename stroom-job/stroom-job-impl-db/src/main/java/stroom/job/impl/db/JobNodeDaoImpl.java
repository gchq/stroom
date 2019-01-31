package stroom.job.impl.db;

import stroom.job.api.JobNode;
import stroom.job.impl.db.stroom.tables.records.JobNodeRecord;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.stroom.Tables.JOB_NODE;

public class JobNodeDaoImpl implements JobNodeDao {

    private final ConnectionProvider connectionProvider;
    private GenericDao<JobNodeRecord, JobNode> dao;

    @Inject
    JobNodeDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        dao = new GenericDao(JOB_NODE, JOB_NODE.ID, JobNode.class, connectionProvider);
    }

     @Override
    public JobNode create(final JobNode job) {
        return dao.create(job);
    }

    @Override
    public JobNode create() {
        throw new RuntimeException("Not implemented yet -- interface inappropriate for use with GenericDao");
    }

    @Override
    public JobNode update(final JobNode jobNode) {
        return dao.update(jobNode);
    }

    @Override
    public int delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<JobNode> fetch(int id) {
        return dao.fetch(id);
    }
}
