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

import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;

/**
 * Represents a node for storage and processing.
 */
public class Node implements HasAuditInfo, SharedObject {
    private static final long serialVersionUID = 3578705325508265924L;

    public static final String ENTITY_TYPE = "Node";

    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private String createUser;
    private Long updateTimeMs;
    private String updateUser;
    private String name;
    private String url;

    /**
     * A number to imply how import that this node is from the POV of being a
     * master node. The bigger the number the better chance it will become a
     * master
     */
    private int priority = 1;
    private boolean enabled = true;

    public Node() {
        // Default constructor necessary for GWT serialisation.
    }

    /**
     * Utility to create a node.
     */
    public static Node create(final String name) {
        final Node node = new Node();
        node.setName(name);
        return node;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

//    @Override
//    protected void toString(final StringBuilder sb) {
//        super.toString(sb);
//        sb.append(", name=");
//        sb.append(getName());
//        sb.append(", clusterCallUrl=");
//        sb.append(url);
//        sb.append(", priority=");
//        sb.append(priority);
//    }
//
//    @Transient
//    @Override
//    public final String getType() {
//        return ENTITY_TYPE;
//    }
//
//    public Node copy() {
//        final Node node = new Node();
//        node.setName(getName());
//        node.priority = priority;
//        node.enabled = enabled;
//        return node;
//    }
}
