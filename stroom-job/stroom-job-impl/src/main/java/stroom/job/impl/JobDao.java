package stroom.job.impl;

import stroom.job.shared.Job;
import stroom.util.shared.ResultPage;

import java.util.Optional;
import java.util.Set;

public interface JobDao {

    Job create(Job job);

    Job update(Job job);

    boolean delete(final int id);

    Optional<Job> fetch(int id);

    ResultPage<Job> find(FindJobCriteria findJobCriteria);

    int deleteOrphans();

    int setJobsEnabled(String nodeName, boolean enabled, final Set<String> includeJobs, final Set<String> excludeJobs);
}
