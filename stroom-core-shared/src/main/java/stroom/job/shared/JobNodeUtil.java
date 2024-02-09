package stroom.job.shared;

import stroom.job.shared.JobNode.JobType;

public class JobNodeUtil {

    public static void setSchedule(final JobNode jobNode, final Schedule schedule) {
        JobType jobType = JobType.UNKNOWN;
        if (schedule.getType() != null) {
            switch (schedule.getType()) {
                case FREQUENCY: {
                    jobType = JobType.FREQUENCY;
                    break;
                }
                case CRON: {
                    jobType = JobType.CRON;
                    break;
                }
            }
        }
        jobNode.setJobType(jobType);
        jobNode.setSchedule(schedule.getExpression());
    }

    public static Schedule getSchedule(final JobNode jobNode) {
        ScheduleType scheduleType = null;
        if (jobNode.getJobType() != null) {
            switch (jobNode.getJobType()) {
                case FREQUENCY: {
                    scheduleType = ScheduleType.FREQUENCY;
                    break;
                }
                case CRON: {
                    scheduleType = ScheduleType.CRON;
                    break;
                }
            }
        }
        if (scheduleType == null) {
            return null;
        }
        return new Schedule(scheduleType, jobNode.getSchedule());
    }
}
