package stroom.job.impl.db;

import stroom.entity.BasicCrudDao;

public interface JobNodeDao extends BasicCrudDao<JobNode> {
    JobNode create(JobNode jobNode);
}
