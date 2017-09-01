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

import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * This class controls the cluster wide properties of a job.
 */
@Entity
@Table(name = "JB", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class Job extends NamedEntity {
    public static final String ENTITY_TYPE = "Job";
    public static final String MANAGE_JOBS_PERMISSION = "Manage Jobs";
    public static final String TABLE_NAME = SQLNameConstants.JOB;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    private static final long serialVersionUID = -2692414627588961565L;
    private boolean enabled = false;

    // Transients
    private String description;
    private boolean advanced;

    @Column(name = SQLNameConstants.ENABLED, nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Transient
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Transient
    public boolean isAdvanced() {
        return advanced;
    }

    public void setAdvanced(boolean advanced) {
        this.advanced = advanced;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", jobName=");
        sb.append(getName());
        sb.append(", enabled=");
        sb.append(enabled);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
