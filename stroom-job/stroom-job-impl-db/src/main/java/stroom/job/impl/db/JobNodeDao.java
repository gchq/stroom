package stroom.job.impl.db;

import stroom.util.entity.BasicCrudDao;

public interface JobNodeDao extends BasicCrudDao<JobNode> {
    JobNode create(JobNode jobNode);
}
