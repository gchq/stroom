/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.jobsystem.shared;

import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.node.shared.Node;
import stroom.util.shared.HasDisplayValue;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Min;

/**
 * This class records and manages all jobs across all nodes.
 */
@Entity
@Table(name = "JB_ND", uniqueConstraints = @UniqueConstraint(columnNames = { "FK_ND_ID", "FK_JB_ID" }) )
public class JobNode extends AuditedEntity {
    private static final long serialVersionUID = 6581293484621389638L;

    public static final String ENTITY_TYPE = "JobNode";

    public static final String TABLE_NAME = SQLNameConstants.JOB + SEP + SQLNameConstants.NODE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;

    public static final String TASK_LIMIT = SQLNameConstants.TASK + SQLNameConstants.LIMIT_SUFFIX;
    public static final String LOCK_TASK_LIMIT = SQLNameConstants.LOCK + SEP + SQLNameConstants.TASK
            + SQLNameConstants.LIMIT_SUFFIX;
    public static final String JOB_TYPE = SQLNameConstants.JOB + SQLNameConstants.TYPE_SUFFIX;

    public static final String SCHEDULE = "SCHEDULE";

    public enum JobType implements HasDisplayValue,HasPrimitiveValue {
        UNKNOWN("UNKNOWN", 0), CRON("Cron", 1), FREQUENCY("Fequency", 2), DISTRIBUTED("Distributed", 3);

        private final String displayValue;
        private final byte primitiveValue;

        public static final PrimitiveValueConverter<JobType> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                JobType.values());

        JobType(final String displayValue, final int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }

    private Node node;
    private Job job;
    private int taskLimit = 20;
    private byte pJobType = JobType.UNKNOWN.getPrimitiveValue();
    private String schedule;
    private boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = Node.FOREIGN_KEY)
    public Node getNode() {
        return node;
    }

    public void setNode(final Node node) {
        this.node = node;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = Job.FOREIGN_KEY)
    public Job getJob() {
        return job;
    }

    public void setJob(final Job job) {
        this.job = job;
    }

    @Column(name = TASK_LIMIT, columnDefinition = INT_UNSIGNED, nullable = false)
    @Min(value = 0)
    public int getTaskLimit() {
        return taskLimit;
    }

    public void setTaskLimit(final int taskLimit) {
        this.taskLimit = taskLimit;
    }

    @Column(name = JOB_TYPE, nullable = false)
    public byte getPJobType() {
        return pJobType;
    }

    public void setPJobType(final byte pJobType) {
        this.pJobType = pJobType;
    }

    @Transient
    public JobType getJobType() {
        return JobType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pJobType);
    }

    public void setJobType(final JobType jobType) {
        this.pJobType = jobType.getPrimitiveValue();
    }

    @Column(name = SCHEDULE)
    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    @Column(name = SQLNameConstants.ENABLED, nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        // Job is lazy so just trace it's id
        if (job != null) {
            sb.append(", jobId=");
            sb.append(job.getId());
        }
        sb.append(", jobType=");
        sb.append(JobType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pJobType).getDisplayValue());
        sb.append(", enabled=");
        sb.append(Boolean.toString(enabled));
        sb.append(", taskLimit=");
        sb.append(getTaskLimit());
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
