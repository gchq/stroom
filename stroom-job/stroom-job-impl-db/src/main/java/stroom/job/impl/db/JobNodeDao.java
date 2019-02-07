package stroom.job.impl.db;

import stroom.db.util.GenericDao;
import stroom.entity.BasicIntCrudDao;
import stroom.job.impl.db.stroom.tables.records.JobNodeRecord;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.stroom.Tables.JOB_NODE;

public class JobNodeDao implements BasicIntCrudDao<JobNode> {

    private GenericDao<JobNodeRecord, JobNode, Integer> dao;

    @Inject
    JobNodeDao(final ConnectionProvider connectionProvider) {
        dao = new GenericDao<>(JOB_NODE, JOB_NODE.ID, JobNode.class, connectionProvider);
    }

    @Override
    public JobNode create(@Nonnull final JobNode job) {
        return dao.create(job);
    }

    @Override
    public JobNode update(@Nonnull final JobNode jobNode) {
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
