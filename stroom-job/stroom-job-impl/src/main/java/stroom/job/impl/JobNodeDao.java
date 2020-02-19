package stroom.job.impl;

import stroom.job.shared.JobNode;
import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface JobNodeDao {
    JobNode create(JobNode jobNode);

    JobNode update(JobNode jobNode);

    boolean delete(final int id);

    Optional<JobNode> fetch(int id);

    ResultPage<JobNode> find(FindJobNodeCriteria findJobNodeCriteria);
}
