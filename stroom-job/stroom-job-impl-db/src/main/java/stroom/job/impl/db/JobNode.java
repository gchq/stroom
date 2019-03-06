package stroom.job.impl.db;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public class JobNode {
    private Integer id;
    private String nodeName;
    private Job job;
    private int taskLimit = 20;
    private byte jobType = JobType.UNKNOWN.getPrimitiveValue();
    private String schedule;
    private boolean enabled;

    public JobNode(){}

    public JobNode(final Integer id, final String nodeName, final Job job, final int taskLimit, final JobType jobType, final String schedule, final boolean enabled){
        this.id = id;
        this.nodeName = nodeName;
        this.job = job;
        this.taskLimit = taskLimit;
        this.jobType = jobType.primitiveValue;
        this.schedule = schedule;
        this.enabled = enabled;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(final Job job) {
        this.job = job;
    }

    public int getTaskLimit() {
        return taskLimit;
    }

    public void setTaskLimit(final int taskLimit) {
        this.taskLimit = taskLimit;
    }

    public JobType getJobType() {
        return JobType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(jobType);
    }

    public void setJobType(final JobType jobType) {
        this.jobType = jobType.getPrimitiveValue();
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public enum JobType implements HasPrimitiveValue {
        UNKNOWN("UNKNOWN", 0), CRON("Cron", 1), FREQUENCY("Fequency", 2), DISTRIBUTED("Distributed", 3);

        public static final PrimitiveValueConverter<JobType> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                JobType.values());
        private final String displayValue;
        private final byte primitiveValue;

        JobType(final String displayValue, final int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
