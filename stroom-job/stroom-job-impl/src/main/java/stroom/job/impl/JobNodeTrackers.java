package stroom.job.impl;

import stroom.job.shared.JobNode;
import stroom.util.scheduler.Scheduler;

import java.util.List;

public interface JobNodeTrackers {

    JobNodeTracker getTrackerForJobName(String jobName);

    List<JobNodeTracker> getDistributedJobNodeTrackers();

    Scheduler getScheduler(JobNode jobNode);
}
