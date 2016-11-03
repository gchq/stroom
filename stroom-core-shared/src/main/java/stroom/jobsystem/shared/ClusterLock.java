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

import stroom.entity.shared.BaseEntitySmall;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * This class controls the cluster-wide properties of a job.
 */
@Entity
@Table(name = "CLSTR_LK", uniqueConstraints = @UniqueConstraint(columnNames = { SQLNameConstants.NAME }) )
public class ClusterLock extends BaseEntitySmall {
    private static final long serialVersionUID = -2692414627588961565L;

    public static final String TABLE_NAME = SQLNameConstants.CLUSTER + SEP + SQLNameConstants.LOCK;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;

    public static final String ENTITY_TYPE = "ClusterLock";

    private String name;

    @Column(name = SQLNameConstants.NAME, nullable = false)
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
