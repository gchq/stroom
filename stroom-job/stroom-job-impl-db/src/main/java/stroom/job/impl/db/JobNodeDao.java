package stroom.job.impl.db;

import stroom.entity.BasicCrudDao;
import stroom.job.api.JobNode;

public interface JobNodeDao extends BasicCrudDao<JobNode> {
    JobNode create(JobNode jobNode);
}
