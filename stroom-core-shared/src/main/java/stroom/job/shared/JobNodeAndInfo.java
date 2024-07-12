package stroom.job.shared;

import stroom.job.shared.JobNode.JobType;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobNodeAndInfo {

    @JsonProperty
    private final JobNode jobNode;

    @JsonProperty
    // Can be set lazily
    private JobNodeInfo jobNodeInfo;

    @JsonCreator
    public JobNodeAndInfo(@JsonProperty("jobNode") final JobNode jobNode,
                          @JsonProperty("jobNodeInfo") final JobNodeInfo jobNodeInfo) {
        this.jobNode = jobNode;
        this.jobNodeInfo = jobNodeInfo;
    }

    public static JobNodeAndInfo withoutInfo(final JobNode jobNode) {
        return new JobNodeAndInfo(jobNode, null);
    }

    public static JobNodeAndInfo empty() {
        return new JobNodeAndInfo(null, null);
    }

    public JobNode getJobNode() {
        return jobNode;
    }

    /**
     * May be null. Intended to be lazily populated as the info must come from the node itself
     */
    public JobNodeInfo getJobNodeInfo() {
        return jobNodeInfo;
    }

    public void setJobNodeInfo(final JobNodeInfo jobNodeInfo) {
        this.jobNodeInfo = jobNodeInfo;
    }

    public void clearJobNodeInfo() {
        this.jobNodeInfo = null;
    }

    @Override
    public String toString() {
        return "JobNodeAndInfo{" +
                "jobNode=" + jobNode +
                ", jobNodeInfo=" + jobNodeInfo +
                '}';
    }

    // -----------------------------
    // Delegate methods from jobNode
    // -----------------------------

    /**
     * @return {@link JobNode#getId()}
     */
    @JsonIgnore
    public Integer getId() {
        return GwtNullSafe.get(jobNode, JobNode::getId);
    }

    @JsonIgnore
    public Job getJob() {
        return GwtNullSafe.get(jobNode, JobNode::getJob);
    }

    @JsonIgnore
    public String getJobName() {
        return GwtNullSafe.get(jobNode, JobNode::getJobName);
    }

    @JsonIgnore
    public JobType getJobType() {
        return GwtNullSafe.get(jobNode, JobNode::getJobType);
    }

    @JsonIgnore
    public String getNodeName() {
        return GwtNullSafe.get(jobNode, JobNode::getNodeName);
    }

    @JsonIgnore
    public int getTaskLimit() {
        return GwtNullSafe.get(jobNode, JobNode::getTaskLimit);
    }

    @JsonIgnore
    public String getSchedule() {
        return GwtNullSafe.get(jobNode, JobNode::getSchedule);
    }

    @JsonIgnore
    public boolean isEnabled() {
        return GwtNullSafe.get(jobNode, JobNode::isEnabled);
    }

    // ---------------------------------
    // Delegate methods from jobNodeInfo
    // ---------------------------------

    @JsonIgnore
    public Integer getCurrentTaskCount() {
        return GwtNullSafe.get(jobNodeInfo, JobNodeInfo::getCurrentTaskCount);
    }

    @JsonIgnore
    public Long getScheduleReferenceTime() {
        return GwtNullSafe.get(jobNodeInfo, JobNodeInfo::getScheduleReferenceTime);
    }

    @JsonIgnore
    public Long getLastExecutedTime() {
        return GwtNullSafe.get(jobNodeInfo, JobNodeInfo::getLastExecutedTime);
    }

    @JsonIgnore
    public Long getNextScheduledTime() {
        return GwtNullSafe.get(jobNodeInfo, JobNodeInfo::getNextScheduledTime);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final JobNodeAndInfo that = (JobNodeAndInfo) object;
        return Objects.equals(jobNode, that.jobNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobNode);
    }
}
