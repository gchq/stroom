package stroom.job.impl.db;

import stroom.db.util.GenericDao;
import stroom.job.impl.db.jooq.tables.records.JobNodeRecord;
import stroom.util.shared.HasIntCrud;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.jooq.Tables.JOB_NODE;

public class JobNodeDao implements HasIntCrud<JobNode> {
    private GenericDao<JobNodeRecord, JobNode, Integer> dao;

    @Inject
    JobNodeDao(final ConnectionProvider connectionProvider) {
        dao = new GenericDao<>(JOB_NODE, JOB_NODE.ID, JobNode.class, connectionProvider);
    }

    @Override
    public JobNode create(@Nonnull final JobNode jobNode) {
        return dao.create(jobNode);
    }

    @Override
    public JobNode update(@Nonnull final JobNode jobNode) {
        return dao.update(jobNode);
    }

    @Override
    public boolean delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<JobNode> fetch(int id) {
        return dao.fetch(id);
    }
}
