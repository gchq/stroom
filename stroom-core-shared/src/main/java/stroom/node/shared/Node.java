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

package stroom.node.shared;

import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * Represents a node for storage and processing.
 */
@Entity
@Table(name = "ND", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class Node extends NamedEntity {
    public static final String TABLE_NAME = SQLNameConstants.NODE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String CLUSTER_URL = SQLNameConstants.CLUSTER + SEP + SQLNameConstants.URL;
    public static final String CONCURRENT_TASKS = SQLNameConstants.CONCURRENT + SEP + SQLNameConstants.TASK;
    public static final String PATH = SQLNameConstants.PATH;
    public static final String NODE_STATUS = SQLNameConstants.NODE + SQLNameConstants.STATUS_SUFFIX;
    public static final String ENTITY_TYPE = "Node";
    public static final String MANAGE_NODES_PERMISSION = "Manage Nodes";
    private static final long serialVersionUID = 3578705325508265924L;
    private String clusterURL;

    /**
     * A number to imply how import that this node is from the POV of being a
     * master node. The bigger the number the better chance it will become a
     * master
     */
    private int priority = 1;
    private Rack rack;
    private boolean enabled = true;

    /**
     * Utility to create a node.
     */
    public static Node create(final Rack rack, final String name) {
        final Node node = new Node();
        node.setRack(rack);
        node.setName(name);
        return node;
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = Rack.FOREIGN_KEY)
    public Rack getRack() {
        return rack;
    }

    public void setRack(final Rack rack) {
        this.rack = rack;
    }

    @Column(name = CLUSTER_URL)
    public String getClusterURL() {
        return clusterURL;
    }

    public void setClusterURL(final String clusterURL) {
        this.clusterURL = clusterURL;
    }

    @Column(name = SQLNameConstants.PRIORITY, columnDefinition = SMALLINT_UNSIGNED, nullable = false)
    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
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
        sb.append(", name=");
        sb.append(getName());
        sb.append(", clusterCallUrl=");
        sb.append(clusterURL);
        sb.append(", priority=");
        sb.append(priority);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
