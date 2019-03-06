package stroom.job.impl;

import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode;
import stroom.util.shared.BaseResultList;

import java.util.Optional;

public interface JobNodeDao {
    JobNode create(JobNode jobNode);

    JobNode update(JobNode jobNode);

    boolean delete(final int id);

    Optional<JobNode> fetch(int id);

    BaseResultList<JobNode> find(FindJobNodeCriteria findJobNodeCriteria);
}
