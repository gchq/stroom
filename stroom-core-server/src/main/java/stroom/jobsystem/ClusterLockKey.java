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

package stroom.jobsystem;

import stroom.util.date.DateUtil;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class ClusterLockKey implements Serializable {
    private static final long serialVersionUID = -5425199832227725803L;

    private final String name;
    private final String nodeName;
    private final long creationTime;

    public ClusterLockKey(final String name, final String nodeName, final long creationTime) {
        this.name = name;
        this.nodeName = nodeName;
        this.creationTime = creationTime;
    }

    public String getName() {
        return name;
    }

    public String getNodeName() {
        return nodeName;
    }

    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(name);
        builder.append(nodeName);
        builder.append(creationTime);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ClusterLockKey)) {
            return false;
        }

        final ClusterLockKey clusterLockKey = (ClusterLockKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(name, clusterLockKey.name);
        builder.append(nodeName, clusterLockKey.nodeName);
        builder.append(creationTime, clusterLockKey.creationTime);

        return builder.isEquals();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public void append(final StringBuilder sb) {
        sb.append("name=");
        sb.append(name);
        sb.append(" node=");
        sb.append(nodeName);
        sb.append(" creationTime=");
        sb.append(DateUtil.createNormalDateTimeString(creationTime));
    }
}
