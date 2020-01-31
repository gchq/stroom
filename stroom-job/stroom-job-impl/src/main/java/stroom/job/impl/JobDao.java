package stroom.job.impl;

import stroom.job.shared.Job;
import stroom.util.shared.BaseResultList;

import java.util.Optional;

public interface JobDao {
    Job create(Job job);

    Job update(Job job);

    boolean delete(final int id);

    Optional<Job> fetch(int id);

    BaseResultList<Job> find(FindJobCriteria findJobCriteria);

    int deleteOrphans();
}
